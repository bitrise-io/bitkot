package bitkot.cache.remote

import bitkot.cache.iface.kDefaultChunkSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RemoteCacheConfig(
    @SerialName("endpoint") val endpoint: String,
    @SerialName("headers") val headers: Map<String, String> = mapOf(),
    // 5 second timeout per message
    @SerialName("message_timeout") val messageTimeout: Int = 5,
    @SerialName("chunk_size") val chunkSize: Int = kDefaultChunkSize,
    // retry overall blob uploads 3 times
    @SerialName("retry_count") var retryCount: Int = 3,
    @SerialName("mbps_write_limit") val mbpsWriteLimit: Long? = 200L,
)