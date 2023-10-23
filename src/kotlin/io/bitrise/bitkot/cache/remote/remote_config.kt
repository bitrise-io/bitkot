package io.bitrise.bitkot.cache.remote

import io.bitrise.bitkot.cache.iface.kDefaultChunkSize
import io.bitrise.bitkot.grpc_utils.channelFromEndpoint
import io.grpc.ManagedChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.*
import java.util.concurrent.Executor

@Serializable
data class ChannelConfig(
    @SerialName("tls_cert_path") val tlsCertPath: String? = null,
    @SerialName("override_authority") val overrideAuthority: String? = null,
    @SerialName("retry_count") val retryCount: Int = 3,
    @SerialName("timeout") val timeout: Long? = null,
)

@Serializable
data class RemoteCacheRpcConfig(
    @SerialName("invocation_id") val invocationId: String = UUID.randomUUID().toString(),
    @SerialName("headers") val headers: Map<String, String> = mapOf(),
    @SerialName("chunk_size") val chunkSize: Int = kDefaultChunkSize,
    @SerialName("mbps_write_limit") val mbpsWriteLimit: Long? = 200L,
    @SerialName("tool_name") val toolName: String = "bitkot_bazel",
)

@Serializable
data class RemoteCacheConfig(
    @SerialName("endpoint") val endpoint: String,
    @SerialName("rpc_config") val rpcConfig: RemoteCacheRpcConfig = RemoteCacheRpcConfig(),
    @SerialName("channel_config") val channelConfig: ChannelConfig = ChannelConfig(),
)

fun RemoteCacheConfig.toManagedChannel(executor: Executor? = null) = channelFromEndpoint(
    endpoint,
    channelConfig.tlsCertPath,
    channelConfig.overrideAuthority,
    channelConfig.retryCount,
    channelConfig.timeout,
    executor,
)