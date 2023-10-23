package io.bitrise.bitkot.grpc_utils

import com.google.protobuf.timestamp
import io.grpc.*
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

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

class TimeOutInterceptor(private val timeout: Long): ClientInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>?,
        callOptions: CallOptions,
        next: io.grpc.Channel
    ): ClientCall<ReqT, RespT> {
        return next.newCall(
            method,
            callOptions.withDeadlineAfter(timeout, TimeUnit.SECONDS),
        );
    }

}

fun channelFromEndpoint(
    endpoint: String,
    tlsCertPath: String? = null,
    overrideAuthority: String? = null,
    retryCount: Int? = null,
    timeout: Long? = null,
    executor: Executor? = null,
): ManagedChannel {
    val customTls = !tlsCertPath.isNullOrEmpty()
    var plainText: Boolean
    val target: String = when {
        endpoint.startsWith("grpc://") -> {
            plainText = true
            endpoint.removePrefix("grpc://")
        }
        endpoint.startsWith("grpcs://") -> {
            plainText = false
            endpoint.removePrefix("grpcs://")
        }
        endpoint.endsWith(":443") -> {
            plainText = false
            endpoint
        }
        else -> {
            plainText = false
            endpoint
        }
    }
    if (customTls) {
        plainText = false
    }
    val builder: ManagedChannelBuilder<*> = if (customTls) {
        NettyChannelBuilder
            .forTarget(target)
            .sslContext(GrpcSslContexts
                .forClient()
                .trustManager(File(tlsCertPath!!))
                .build())
    } else {
        ManagedChannelBuilder.forTarget(target)
    }

    retryCount?.also { builder.enableRetry().maxRetryAttempts(it) }
    timeout?.also { builder.intercept(TimeOutInterceptor(it)) }
    executor?.also { builder.executor(executor) }

    if (plainText) {
        builder.usePlaintext()
    } else {
        overrideAuthority?.also { builder.overrideAuthority(it) }
        builder.useTransportSecurity()
    }
    return builder.build()
}

fun Instant.toTimestamp() = timestamp {
    this.seconds = epochSecond
    this.nanos = nano
}
