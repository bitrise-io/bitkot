package bitkot.cache

import bitkot.cache.iface.ICache
import bitkot.cache.iface.ICacheWithLocal
import bitkot.cache.iface.collectFrom
import bitkot.cache.local.ILocalCache
import bitkot.cache.local.createLocalCache
import bitkot.cache.remote.IRemoteCache
import bitkot.cache.remote.RemoteCacheConfig
import bitkot.cache.remote.createRemoteCache
import bitkot.utils.CompositeDisposable
import bitkot.utils.createInnerTmpHardlinkTo
import bitkot.utils.inputStreamFlow
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import java.util.*
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.RuntimeException
import kotlin.io.path.div
import bitkot.utils.*
import bitkot.proto_utils.toFileName

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

private class RemoteCacheWrapper(
    binPath: Path,
    dir: Path,
): ICache, IRemoteCache {

    private val tmpDir = (dir / "tmp").apply { toFile().mkdirs() }

    private val proc: Process = ProcessBuilder(listOf(
        binPath.toString(),
        "--dir=$dir/data",
        "--max_size=1",
    ))
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    private val cacheConfig = RemoteCacheConfig(
        endpoint = "grpc://127.0.0.1:9092",
        headers = mapOf(),
    )

    private val cache = createRemoteCache(dir, cacheConfig)

    override suspend fun getActionResult(digest: Digest) = cache.getActionResult(digest)
    override suspend fun upsertActionResult(digest: Digest, result: ActionResult) = cache.upsertActionResult(digest, result)
    override suspend fun checkHasMovable(digest: Digest): Path? {
        val result = tmpDir / digest.toFileName()
        result.collectFromFlow(read(digest) ?: return null)
        return result
    }

    override suspend fun writeFrom(digest: Digest, src: Path) {
        val inputFlow = tmpDir
            .createInnerTmpHardlinkTo(src)
            ?.inputStreamFlow(true, cacheConfig.chunkSize)
            ?: return

        write(digest).collectFrom(inputFlow)
    }

    override suspend fun read(digest: Digest) = cache.read(digest)
    override fun write(digest: Digest) = cache.write(digest)

    override suspend fun findMissing(digests: List<Digest>) = cache.findMissing(digests)

    override suspend fun dispose() {
        proc.destroy()
        withContext(Dispatchers.IO) {
            proc.waitFor(2, TimeUnit.SECONDS)
        }
    }

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