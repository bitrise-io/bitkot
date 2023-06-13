package bitkot.cache.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RemoteCacheConfig(
    @SerialName("endpoint") val endpoint: String,
    @SerialName("headers") val headers: Map<String, String>,
)