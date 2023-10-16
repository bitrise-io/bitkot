package io.bitrise.bitkot.cache.test

import com.google.protobuf.ByteString
import io.bitrise.bitkot.utils.*
import io.bitrise.bitkot.cache.local.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

class LocalCacheTest {

    companion object {
        private val localTempDir = createTempDirectory()

        @JvmStatic
        @AfterClass
        fun cleanup() {
            localTempDir.deleteDir()
        }
    }

    private fun createLocalCache(config: LocalCacheConfig = LocalCacheConfig(
        eraseOnStart = true,
        maxSizeMb = null,
    )): IInnerLocalCache {
        return createInnerLocalCache(localTempDir, config)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun simple() = runTest {
        val cache = createLocalCache()
        val data = ByteString.copyFrom(randomString(2048).toByteArray())

        cache.writeWhole("testdb", "testkey", data)
        val dataRead = cache.readWhole("testdb", "testkey")!!

        assertEquals(data, dataRead)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testLru() = runTest {
        // Fill cache
        run {
            val cache = createLocalCache()
            for (i in 0 until 13) {
                cache.writeWhole(
                    "testdb",
                    "testkey$i",
                    ByteString.copyFrom(randomString(1024 * 1024).toByteArray())
                )
            }
            assertEquals(cache.size(), 13 * 1024 * 1024)
        }
        // Now run cache with limit of 10mb
        run {
            val cache = createLocalCache(LocalCacheConfig(
                eraseOnStart = false,
                maxSizeMb = 10
            ))

            // Check 3 files got deleted
            assertEquals(cache.size(), 10 * 1024 * 1024)

            // Read some keys
            for (i in 3 until 8) {
                cache.readWhole(
                    "testdb",
                    "testkey$i"
                )!!
            }

            cache.writeWhole(
                "testdb",
                "testkeyoverflow1",
                ByteString.copyFrom(randomString(1024 * 1024).toByteArray())
            )

            // Check size is still 10mb
            assertEquals(cache.size(), 10 * 1024 * 1024)

            cache.writeWhole(
                "testdb",
                "testkeyoverflow2",
                ByteString.copyFrom(randomString(512 * 1024).toByteArray())
            )

            // Check size is 9.5 mb now
            assertEquals(cache.size(), (10 * 1024 * 1024) - (512 * 1024))
        }

    }
}