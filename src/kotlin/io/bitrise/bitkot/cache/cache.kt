package io.bitrise.bitkot.cache

import io.bitrise.bitkot.cache.iface.ICache
import io.bitrise.bitkot.cache.iface.ICacheWithLocal
import io.bitrise.bitkot.cache.iface.collectFrom
import io.bitrise.bitkot.cache.local.ILocalCache
import io.bitrise.bitkot.cache.local.createLocalCache
import io.bitrise.bitkot.cache.remote.IRemoteCache
import io.bitrise.bitkot.cache.remote.createRemoteCache
import io.bitrise.bitkot.utils.CompositeDisposable
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.RuntimeException

open class Cache(
    private val remote: IRemoteCache,
    private val local: ILocalCache
): ICacheWithLocal, CompositeDisposable() {

    init {
        addDisposable(remote)
        addDisposable(local)
    }

    override fun localCache(db: String) = local.localCache(db)

    override suspend fun getActionResult(digest: Digest): ActionResult? {
        val ar = remote.getActionResult(digest) ?: return local.getActionResult(digest)
        local.upsertActionResult(digest, ar)
        return ar
    }

    override suspend fun upsertActionResult(digest: Digest, result: ActionResult): Unit = coroutineScope {
        awaitAll(
            async { remote.upsertActionResult(digest, result) },
            async { local.upsertActionResult(digest, result) },
        )
    }

    override suspend fun checkHasMovable(digest: Digest): Path? {
        val localFile = local.checkHasMovable(digest)
        if (localFile != null) {
            return localFile
        }

        val remoteFlow = remote.read(digest) ?: return null
        local.write(digest).collectFrom(remoteFlow)
        return local.checkHasMovable(digest)
    }

    override suspend fun writeFrom(digest: Digest, src: Path) {
        local.writeFrom(digest, src)
        val readFlow = local.read(digest)
            ?: throw RuntimeException("Can't read freshly copied to local cache")
        remote.write(digest).collectFrom(readFlow)
    }

    override suspend fun findMissing(digests: List<Digest>)
        = remote.findMissing(local.findMissing(digests))

}


fun createCache(cacheConfig: CacheConfig): ICache {
    if (cacheConfig.remote != null && cacheConfig.local != null) {
        return Cache(
            createRemoteCache(cacheConfig.directory, cacheConfig.remote),
            createLocalCache(cacheConfig.directory, cacheConfig.local),
        )
    } else if (cacheConfig.remote != null) {
        return createRemoteCache(cacheConfig.directory, cacheConfig.remote)
    } else if (cacheConfig.local != null) {
        return createLocalCache(cacheConfig.directory, cacheConfig.local)
    }

    throw RuntimeException("cache config is empty")
}