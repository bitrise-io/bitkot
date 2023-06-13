package bitkot.cache.iface

import bitkot.utils.Disposable
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.file.Path

interface IBlobFinder {
    suspend fun findMissing(digests: List<Digest>): List<Digest>
}

interface IWriter: Closeable {
    suspend fun write(data: ByteString)
    suspend fun write(data: ByteArray)
    suspend fun commit()
}

suspend fun IWriter.collectFrom(dataFlow: Flow<ByteString>) {
    try {
        dataFlow
            .onEach { write(it) }
            .onCompletion { commit() }
            .collect()
    } finally {
        withContext(Dispatchers.IO) {
            close()
        }
    }
}

interface IStreamCache {
    suspend fun read(digest: Digest): Flow<ByteString>?
    fun write(digest: Digest): IWriter

}

interface IActionCache {
    suspend fun getActionResult(digest: Digest): ActionResult?
    suspend fun upsertActionResult(digest: Digest, result: ActionResult)
}

interface IBlobCache {
    suspend fun checkHasMovable(digest: Digest): Path?
    suspend fun writeFrom(digest: Digest, src: Path)
}

interface ILocalDBCache: IBlobCache, IStreamCache, IBlobFinder

interface ICache: IActionCache, IBlobCache, IBlobFinder, Disposable
