package io.bitrise.bitkot.utils

import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Throws(IOException::class)
fun readByteStringChunk(`in`: InputStream, chunkSize: Int): ByteString {
    val buf = ByteArray(chunkSize)
    var bytesRead = 0
    while (bytesRead < chunkSize) {
        val count = `in`.read(buf, bytesRead, chunkSize - bytesRead)
        if (count == -1) {
            break
        }
        bytesRead += count
    }
    return if (bytesRead > 0)
        // Always make a copy since InputStream could steal a reference to buf.
        ByteString.copyFrom(buf, 0, bytesRead)
    else
        ByteString.EMPTY
}

fun Path.inputStreamFlow(delete: Boolean, chunkSize: Int): Flow<ByteString> {
    return flow {
        try {
            val readStream = inputStream()
            while(true) {
                val data = readByteStringChunk(readStream, chunkSize)
                if (data.isEmpty) {
                    break
                }
                emit(data)
            }
        } finally {
            if (delete) deleteIfExists()
        }
    }
}

suspend fun Path.collectFromFlow(flow: Flow<ByteString>) {
    val stream = outputStream()
    flow.onEach { it.writeTo(stream) }
        .onCompletion { stream.close() }
        .collect()
}

fun Process.destroyWithDescendants() {
    this.toHandle()
        .descendants()
        .forEach {
            it.destroyForcibly()
        }
    this.destroyForcibly()
}


class BSFlowAsInputStream(
    flow: Flow<ByteString>,
    coroutineScope: CoroutineScope,
): InputStream() {
    private val chan = Channel<ByteString?>(Channel.RENDEZVOUS)
    private val chanIt = chan.iterator()
    private var closed = false
    private val job = coroutineScope.launch {
        flow.collect(chan::send)
        chan.send(null)
    }
    private var curData: ByteString? = null
    private var curDataIt: ByteString.ByteIterator? = null

    var bytesRead: Long = 0

    override fun read(): Int {
        if (closed)
            return -1

        val resByte = readDetail()
            ?: return -1

        return (resByte.toUInt() and 0xFFu).toInt()
    }

    private fun readDetail(): Byte? {
        curDataIt?.let {
            if (!it.hasNext()) {
                return@let
            }
            ++bytesRead
            return it.nextByte()
        }
        curData = null
        curDataIt = null

        if (!runBlocking { chanIt.hasNext() }) {
            close()
            return null
        }

        curData = chanIt.next()
        if (curData != null) {
            curDataIt = curData?.iterator()
            return readDetail()
        } else {
            close()
            return null
        }
    }

    override fun close() {
        super.close()
        closed = true
        chan.close()
        job.cancel()
    }
}

fun Flow<ByteString>.asInputStream(coroutineScope: CoroutineScope)
    = BSFlowAsInputStream(this, coroutineScope)
