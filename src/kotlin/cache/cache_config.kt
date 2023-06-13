package bitkot.cache

import bitkot.cache.remote.RemoteCacheConfig
import bitkot.cache.local.LocalCacheConfig

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.nio.file.Path

@Serializable
data class CacheConfig(
    @SerialName("directory") val directory: Path,
    @SerialName("local") val local: LocalCacheConfig? = null,
    @SerialName("remote") val remote: RemoteCacheConfig? = null,
)