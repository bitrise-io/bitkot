package bitkot.utils

import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

const val kMaxChunkSize = 2 * 1024 * 1024

fun Path.inputStreamFlow(delete: Boolean): Flow<ByteString>? {
    val readStream = inputStream()
    return flow {
        try {
            while(true) {
                val data = ByteString.readFrom(readStream, kMaxChunkSize)
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


