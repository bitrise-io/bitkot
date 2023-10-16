package io.bitrise.bitkot.utils

import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

fun Path.inputStreamFlow(delete: Boolean, chunkSize: Int): Flow<ByteString>? {
    val readStream = inputStream()
    return flow {
        try {
            while(true) {
                val data = ByteString.readFrom(readStream, chunkSize)
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


