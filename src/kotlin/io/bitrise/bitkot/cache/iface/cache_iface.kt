package io.bitrise.bitkot.cache.iface

import io.bitrise.bitkot.utils.Disposable
import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.Digest
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Path

const val kDefaultChunkSize = 1024 * 1024

interface IBlobFinder {
    suspend fun findMissing(digests: List<Digest>): List<Digest>
}

interface IWriter: Closeable {
    suspend fun write(data: ByteString)
    suspend fun write(data: ByteArray)
    suspend fun commit()
}

fun IWriter.asOutputStream() = object : OutputStream() {
    var byteBuffer = ByteArray(kDefaultChunkSize)
    var curIdx = 0

    override fun write(b: Int) {
        val byteBuffer = ByteArray(kDefaultChunkSize)
        byteBuffer[curIdx++] = b.toByte()
        if (curIdx == kDefaultChunkSize)
            flush()
    }

    override fun flush() {
        super.flush()
        runBlocking {
            this@asOutputStream.write(byteBuffer)
        }
        byteBuffer = ByteArray(kDefaultChunkSize)
        curIdx = 0
    }

    override fun close() {
        super.close()
        flush()
    }

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

interface ICacheWithLocal: ICache {
    fun localCache(db: String): ILocalDBCache
}
