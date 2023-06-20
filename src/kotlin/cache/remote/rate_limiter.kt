package bitkot.cache.remote

import bitkot.utils.CompositeDisposable
import kotlinx.coroutines.*
import kotlin.time.Duration

class RateLimiter(
    private val capacity: Long,
    private val period: Duration,
): CompositeDisposable() {

    private class Waiter(var amount: Long) {
        private val completeEvent = CompletableDeferred<Unit>()

        fun complete() = completeEvent.complete(Unit)
        suspend fun await() = completeEvent.await()
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private var curCapacity = capacity
    private var quotaWaiters = mutableListOf<Waiter>()

    init {
        scope.launch {
            while(true) {
                delay(period)
                fullfill()
            }
        }
        addDisposable(scope)
    }

    private fun fullfill() = synchronized(this) {
        curCapacity = capacity
        val waitIter = quotaWaiters.iterator()
        while (curCapacity != 0L && waitIter.hasNext()) {
            val curWaiter = waitIter.next()
            if (curWaiter.amount > curCapacity) {
                curCapacity = 0
                break
            } else {
                curCapacity -= curWaiter.amount
                curWaiter.complete()
                waitIter.remove()
            }
        }
    }

    suspend fun awaitQuota(amount: Long) {
        val waiter = synchronized(this) {
            if (amount < curCapacity) {
                curCapacity -= amount
                return
            } else {
                val waiter = Waiter(amount - curCapacity)
                curCapacity = 0
                quotaWaiters.add(waiter)
                return@synchronized waiter
            }
        }
        waiter.await()
    }

}