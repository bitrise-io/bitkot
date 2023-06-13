package bitkot.cache.local

import bitkot.cache.iface.*
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import java.nio.file.Path
import bitkot.proto_utils.toFileName
import bitkot.proto_utils.parseDigestFromFilename

import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.Flow
import java.util.*

interface ILocalCache: ICache, IStreamCache

private open class LocalDBCache(
    private val db: String,
    protected val inner: IInnerLocalCache,
): ILocalDBCache {

    override suspend fun checkHasMovable(digest: Digest) = inner.checkHasMovable(
        db,
        digest.toFileName()
    )

    override suspend fun read(digest: Digest): Flow<ByteString>? = inner.read(
        db,
        digest.toFileName()
    )

    override fun write(digest: Digest) = inner.write(
        db,
        UUID.randomUUID().toString(),
        digest.toFileName()
    )

    override suspend fun writeFrom(digest: Digest, src: Path): Unit  = inner.saveInto(
        db,
        digest.toFileName(),
        src
    )

    override suspend fun findMissing(digests: List<Digest>) = inner.findMissing(
        db,
        digests.map(Digest::toFileName)
    ).map(::parseDigestFromFilename)
}

private class LocalCache(inner: IInnerLocalCache)
: LocalDBCache("blobs", inner)
, ILocalCache {
    override suspend fun getActionResult(digest: Digest): ActionResult? {
        val data = inner.readWhole("ac", digest.toFileName())
            ?: return null

        return ActionResult.parseFrom(data)
    }

    override suspend fun upsertActionResult(digest: Digest, result: ActionResult) {
        inner.writeWhole("ac", digest.toFileName(), result.toByteString())
    }

//    override fun localCache(db: String): ILocalDBCache = LocalDBCache(db, inner)

    override suspend fun dispose() {}

}

fun createLocalCache(directory: Path, config: LocalCacheConfig): ILocalCache
    = LocalCache(createInnerLocalCache(directory, config))