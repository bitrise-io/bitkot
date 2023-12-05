package io.bitrise.bitkot.utils

import com.google.protobuf.ByteString
import kotlin.coroutines.*
import kotlinx.coroutines.*
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.io.InputStream
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

class TaskExecutorPool(size: Int, poolName: String): CompositeDisposable() {
    @OptIn(DelicateCoroutinesApi::class)
    private val executors = List(size) { newSingleThreadContext("$poolName#$it") }

    private val freeExecutors = Channel<ExecutorCoroutineDispatcher>(size)
        .also { queue -> executors.onEach { queue.trySend(it).getOrThrow() } }

    suspend fun <T, R> runTasks(values: Sequence<T>, handler: suspend (value: T) -> R): Flow<Result<R>> {
        return channelFlow {
            for (value in values) {
                val executor = freeExecutors.receive()
                launch(executor) {
                    try {
                        send(runCatching { handler(value) })
                    } finally {
                        freeExecutors.trySend(executor).getOrThrow()
                    }
                }
            }
        }
    }
}

class RetriesExhaustedException(cause: Throwable?) : Throwable(cause = cause)

internal suspend fun withRetryAndTimeout(
    retryCount: Int,
    timeoutMillis: Long,
    task: suspend (a: Int) -> Unit,
    onError: (t: Throwable, a: Int, d: Long) -> Unit = {_,_,_ -> })
{
    var cause: Throwable? = null
    var backoffFactor = 1L
    for (attemptCount in 1 .. retryCount+1) {
        val attemptStart = System.currentTimeMillis()
        try {
            withTimeout(timeoutMillis) {
                task(attemptCount)
            }
            // if the user wants to signal that the task wasn't successful, they should just throw.
            // if the task timed out, an error will have been thrown.
            // if we reach this point, the task was completed, so just return and exit the loop.
            return
        } catch (t: Throwable) {
            cause = t
            onError(t, attemptCount, System.currentTimeMillis() - attemptStart)
        }
        // backoff
        delay(1000L*backoffFactor)
        backoffFactor *= 2
    }
    throw RetriesExhaustedException(cause = cause)
}
