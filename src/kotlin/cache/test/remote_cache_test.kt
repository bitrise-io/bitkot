package bitkot.cache.test

import bitkot.cache.remote.RateLimiter
import bitkot.utils.CompositeDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RemoteCacheTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rateLimiter() = runTest {
        val disposable = CompositeDisposable()
        val awaiten = AtomicLong(0)
        val rateLimiter = RateLimiter(10, 1L.seconds)
        disposable.addDisposable(rateLimiter)
        for (i in 0..20) {
            disposable.addDisposable(launch {
                rateLimiter.awaitQuota(3)
                awaiten.incrementAndGet()
            })
        }
        withContext(Dispatchers.Default) { delay(4L.seconds) }
        disposable.dispose()
        assertEquals(awaiten.get(), 12)
    }

}
