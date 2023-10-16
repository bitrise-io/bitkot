package io.bitrise.bitkot.grpc_utils

import com.google.protobuf.timestamp
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant

fun Throwable.isNotFound() = this is StatusException
        && this.status.code == Status.Code.NOT_FOUND

fun <R, T : R> Result<T>.getOrNullIfNotFound() =
    when (val exception = exceptionOrNull()) {
        null -> getOrThrow()
        else -> if (exception.isNotFound()) null
                else throw exception
    }

suspend fun<T> Flow<T>.safeCheckFirstNotFoundWithNull(): Flow<T>? {
    val channel = Channel<T>(1)
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            this@safeCheckFirstNotFoundWithNull.collect(channel::send)
            channel.close()
        } catch (e: Throwable) {
            channel.close(e)
        }
    }
    val first: T
    try {
        first = channel.receive()
    } catch (e: Throwable) {
        scope.cancel()
        if (e.isNotFound())
            return null
        throw e
    }

    return flow {
        try {
            emit(first)
            emitAll(channel)
        } finally {
            scope.cancel()
        }
    }
}

private fun parseEndpoint(endpoint: String): Pair<Boolean, String> {
    if (endpoint.startsWith("grpc://"))
        return Pair(false, endpoint.substring(7))
    else if (endpoint.startsWith("grpcs://"))
        return Pair(true, endpoint.substring(8))

    throw RuntimeException("Unknown endpoint format $endpoint")
}

fun channelFromEndpoint(endpointRaw: String): ManagedChannel {
    val (isSSL, endpoint) = parseEndpoint(endpointRaw)

    return ManagedChannelBuilder
        .forTarget(endpoint)
        .apply { if (!isSSL) usePlaintext() }
        .build()
}

fun Instant.toTimestamp() = timestamp {
    this.seconds = epochSecond
    this.nanos = nano
}
