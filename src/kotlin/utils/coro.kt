package bitkot.utils

import kotlin.coroutines.*
import kotlinx.coroutines.*
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import java.io.Closeable
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

fun interface Disposable {
    suspend fun dispose()
}

fun Job.asDisposable(): Disposable = Disposable {
    this.cancel()
}

fun CoroutineScope.asDisposable(): Disposable = Disposable {
    this.cancel()
}

fun Disposable.disposeSync() = runBlocking { dispose() }

open class CompositeDisposable: Disposable, Closeable {
    private val innerDisposables = Collections.synchronizedList(mutableListOf<Disposable>())

    fun addDisposable(s: CoroutineScope) = addDisposable(s.asDisposable())
    fun addDisposable(j: Job) = addDisposable(j.asDisposable())

    fun addDisposable(d: Disposable): Closeable {
        innerDisposables.add(d)
        return Closeable {
            if (!innerDisposables.remove(d))
                return@Closeable

            d.disposeSync()
        }
    }

    override suspend fun dispose() {
        while (true) {
            val disposable = synchronized(this) {
                innerDisposables.removeFirstOrNull()
            } ?: return

            disposable.dispose()
        }
    }

    override fun close() {
        disposeSync()
    }

}

open class SafeScope: CompositeDisposable() {
    private val isForceDispose = AtomicBoolean(false)
    private val disposeThread = Thread {
        isForceDispose.set(true)
        disposeSync()
    }
    init { Runtime.getRuntime().addShutdownHook(disposeThread) }

    override suspend fun dispose() {
        if (!isForceDispose.get())
            Runtime.getRuntime().removeShutdownHook(disposeThread)

        super.dispose()
    }

}

class CoroutineListener<T, F: Future<T>>(private val continuation: Continuation<T>): GenericFutureListener<F> {
    override fun operationComplete(future: F) {
        try {
            continuation.resume(future.get())
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }
}

suspend inline fun <T> Future<T>.suspendAwait(): T {
    if (isDone) {
        @Suppress("BlockingMethodInNonBlockingContext")
        return get()
    }

    return suspendCoroutine { continuation ->
        addListener(CoroutineListener(continuation))
    }
}

fun startCoroTimer(
    delayMillis: Long = 0,
    repeatMillis: Long = 0,
    action: suspend () -> Boolean
) = CoroutineScope(Dispatchers.IO).launch {
    delay(delayMillis)
    if (repeatMillis > 0) {
        while (action()) {
            delay(repeatMillis)
        }
    } else {
        action()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> CoroutineScope.mergeChannels(channels: List<ReceiveChannel<T>>) : ReceiveChannel<T> {
    return produce {
        channels.forEach {
            launch { it.consumeEach { send(it) }}
        }
    }
}

private class ThrottleCombineValues<T>(
    private val size: Int,
    private val scope: ProducerScope<List<List<T>>>
) {
    private var values = Array<MutableList<T>>(size) { mutableListOf() }

    fun add(idx: Int, value: T) {
        values[idx].add(value)
    }

    private fun swapValues(): List<List<T>>? {
        if (values.all(List<T>::isEmpty)) {
            return null
        }
        val curValues = values
        values = Array(size) { mutableListOf() }
        return curValues.map { it.toList() }
    }

    suspend fun send() = swapValues()?.run {
        scope.send(this)
    }

    fun trySend() = swapValues()?.run {
        scope.trySend(this)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> throttleCombine(flows: List<Flow<T>>, periodMillis: Long): Flow<List<List<T>>> {
    require(periodMillis > 0) { "period should be positive" }

    return channelFlow {
        val values = ThrottleCombineValues(flows.size, this)

        flows.first().apply {
            onCompletion {
                values.send()
            }
        }

        invokeOnClose {
            values.trySend()
        }

        flows.withIndex().forEach { (i, f) ->
            launch {
                f.collect { value ->
                    values.add(i, value)
                }
            }
        }

        while (true) {
            delay(periodMillis)
            values.send()
        }
    }
}

@ExperimentalCoroutinesApi
fun <T> Flow<T>.throttleCombine(periodMillis: Long) = throttleCombine(listOf(this), periodMillis)
    .map { it.first() }