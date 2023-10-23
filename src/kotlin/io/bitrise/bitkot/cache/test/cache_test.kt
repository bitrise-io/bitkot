package io.bitrise.bitkot.cache.test

import io.bitrise.bitkot.cache.Cache
import io.bitrise.bitkot.cache.iface.*
import io.bitrise.bitkot.cache.local.ILocalCache
import io.bitrise.bitkot.cache.local.LocalCacheConfig
import io.bitrise.bitkot.cache.local.createLocalCache
import io.bitrise.bitkot.cache.remote.IRemoteCache
import io.bitrise.bitkot.cache.remote.RemoteCacheConfig
import io.bitrise.bitkot.cache.remote.createRemoteCache
import io.bitrise.bitkot.proto_utils.calculateDigest
import io.bitrise.bitkot.proto_utils.toFileName
import io.bitrise.bitkot.utils.*
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import com.google.devtools.build.runfiles.Runfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteCacheWrapper(binPath: Path, dir: Path): ICache, IRemoteCache {

    private val proc: Process = ProcessBuilder(listOf(
        binPath.toString(),
        "--dir=$dir/data",
        "--max_size=1",
    ))
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    private val cache = createRemoteCache(
        dir,
        RemoteCacheConfig(endpoint = "grpc://127.0.0.1:9092")
    )

    override suspend fun getActionResult(digest: Digest) = cache.getActionResult(digest)
    override suspend fun upsertActionResult(digest: Digest, result: ActionResult) = cache.upsertActionResult(digest, result)
    override suspend fun checkHasMovable(digest: Digest): Path? = cache.checkHasMovable(digest)
    override suspend fun writeFrom(digest: Digest, src: Path) = cache.writeFrom(digest, src)
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


class CacheTest {
    companion object {
        private val runfiles: Runfiles.Preloaded = Runfiles.preload()
        private val tmpDir = createTempDirectory()
        private val localTempDir = createTempDirectory()
        private val remoteTempDir = createTempDirectory()

        @JvmStatic
        @AfterClass
        fun cleanup() {
            localTempDir.deleteDir()
            remoteTempDir.deleteDir()
            tmpDir.deleteDir()
        }

    }

    @Before
    fun prepare() {
        tmpDir.apply {
            deleteDir()
            createDirectories()
        }
    }

    private fun createLocalCache(): ILocalCache {
        localTempDir.deleteDir()
        return createLocalCache(
            localTempDir,
            LocalCacheConfig(eraseOnStart = true)
        )
    }

    private fun createRemoteCache(): RemoteCacheWrapper {
        val bazelRemotePath = runfiles
            .unmapped()
            .rlocation("com_github_bitrise_io_bitkot/bazel_remote");

        remoteTempDir.deleteDir()
        remoteTempDir.deleteDir()

        return RemoteCacheWrapper(Paths.get(bazelRemotePath), remoteTempDir)
    }

    private fun createHybridCache(): ICache = Cache(
        createRemoteCache(),
        createLocalCache()
    )

    private fun writeRandomFile(size: Int): Path {
        val dst = tmpDir / UUID.randomUUID().toString()
        val out = dst.outputStream()
        out.write(randomString(size).toByteArray())
        return dst
    }

    private fun runCacheTest(cache: ICache, test: (ICache) -> Unit) {
        try {
            test(cache)
        } finally {
            cache.disposeSync()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun testSimple(cache: ICache) = runTest {
        val file = writeRandomFile(2048)
        val fileDigest = file.calculateDigest()

        assertEquals(fileDigest.sizeBytes, 2048)

        assertEquals(
            cache.findMissing(listOf(fileDigest)),
            listOf(fileDigest)
        )

        assertNull(cache.checkHasMovable(fileDigest))

        cache.writeFrom(fileDigest, file)

        assertEquals(
            cache.findMissing(listOf(fileDigest)),
            listOf()
        )

        val copy = assertNotNull(cache.checkHasMovable(fileDigest))

        assertEquals(
            copy.calculateDigest(),
            fileDigest
        )
    }

    @Test
    fun simpleLocal() = runCacheTest(createLocalCache(), ::testSimple)

    @Test
    fun simpleRemote() = runCacheTest(createRemoteCache(), ::testSimple)

    @Test
    fun simpleHybrid() = runCacheTest(createHybridCache(), ::testSimple)
}