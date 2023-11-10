package io.bitrise.bitkot.utils

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import java.time.Duration

fun runWithTimeout(callable: () -> Unit, duration: Duration): Throwable? {
    val executor = Executors.newSingleThreadExecutor()
    val future: Future<Throwable?> = executor.submit(Callable<Throwable?> {
        kotlin.runCatching { callable() }.exceptionOrNull()
    })
    executor.shutdown()
    return try {
        future.get(duration.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        future.cancel(true)
        e
    } catch (e: ExecutionException) {
        when (val t = e.cause) {
            is Error -> (t as Error?)!!
            is Exception -> (t as Exception?)!!
            else -> IllegalStateException(t)
        }
    }
}