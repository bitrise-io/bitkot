package bitkot.cache.local

import bitkot.cache.iface.kDefaultChunkSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class LocalCacheConfig(
    @SerialName("use_hardlinks") val useHardlinks: Boolean = true,
    @SerialName("erase_on_start") val eraseOnStart: Boolean = false,
    @SerialName("max_size_mb") val maxSizeMb: Long? = null,
    @SerialName("chunk_size") val chunkSize: Int = kDefaultChunkSize,
)