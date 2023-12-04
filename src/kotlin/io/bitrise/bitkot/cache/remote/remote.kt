package io.bitrise.bitkot.cache.remote

import io.bitrise.bitkot.cache.iface.*
import io.bitrise.bitkot.grpc_utils.getOrNullIfNotFound
import io.bitrise.bitkot.utils.Disposable
import io.bitrise.bitkot.utils.createInnerTmpHardlinkTo
import io.bitrise.bitkot.utils.inputStreamFlow
import com.google.protobuf.ByteString
import build.bazel.remote.execution.v2.*
import com.google.bytestream.*
import io.grpc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.io.path.div
import io.bitrise.bitkot.utils.*
import io.bitrise.bitkot.proto_utils.toFileName
import kotlin.time.Duration.Companion.seconds

const val zeroHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"


interface IRemoteCache: ICache, IStreamCache, Disposable {
    suspend fun serverCapabilities(): ServerCapabilities
}

private class ChannelWriter(consumer: suspend (Flow<ByteString>) -> Unit): IWriter {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channel = Channel<ByteString>(0)
    private val deferred = scope
        .async { consumer(channel.consumeAsFlow()) }

    override suspend fun write(data: ByteString)
        = channel.send(data)

    override suspend fun write(data: ByteArray)
        = channel.send(ByteString.copyFrom(data))

    override suspend fun commit() {
        channel.send(ByteString.EMPTY)
        // actually, need to close the connection here which sends an EOF; which means "closing the channel"
        // because the kotlin grpc impl uses this channel interface, but this is NOT "closing the grpc channel"
        channel.close()
        deferred.await()
    }

    override fun close() {
        channel.close()
        deferred.cancel()
    }
}

private const val binaryRequestMetadataKey = "build.bazel.remote.execution.v2.requestmetadata-bin"

class RemoteCache(
    directory: Path,
    private val channel: ManagedChannel,
    private val config: RemoteCacheRpcConfig,
): IRemoteCache {
    val tmpDir = (directory / "remote_tmp")
        .apply { toFile().mkdirs() }
    private val requestMetadata = Metadata().apply {
        config.headers.forEach {
            put(Metadata.Key.of(it.key, Metadata.ASCII_STRING_MARSHALLER), it.value)
        }
        put(Metadata.Key.of(binaryRequestMetadataKey, Metadata.BINARY_BYTE_MARSHALLER),
            requestMetadata {
                toolInvocationId = config.invocationId
                toolDetails = toolDetails {
                    toolName = config.toolName
                }
            }.toByteArray()
        )
    }
    private val clientCallOptions = CallOptions.DEFAULT.withCallCredentials(object: CallCredentials() {
        override fun applyRequestMetadata(requestInfo: RequestInfo?, appExecutor: Executor?, applier: MetadataApplier?) {
            applier?.apply(requestMetadata)
        }
        override fun thisUsesUnstableApi() {}
    })

    private var writeEnabled: Boolean? = null
    private val actionCache = ActionCacheGrpcKt.ActionCacheCoroutineStub(channel, clientCallOptions)
    private val cas = ContentAddressableStorageGrpcKt.ContentAddressableStorageCoroutineStub(channel, clientCallOptions)
    private val bs = ByteStreamGrpcKt.ByteStreamCoroutineStub(channel, clientCallOptions)
    private val caps = CapabilitiesGrpcKt.CapabilitiesCoroutineStub(channel, clientCallOptions)

    private val rateLimiter = config.mbpsWriteLimit?.let { RateLimiter(it * 128L * 1024L, 1L.seconds) }

    override suspend fun serverCapabilities() = caps.getCapabilities(getCapabilitiesRequest {})

    override suspend fun getActionResult(digest: Digest): ActionResult? = runCatching {
        actionCache.getActionResult(getActionResultRequest {
            actionDigest = digest
        })
    }.getOrNullIfNotFound()

    override suspend fun upsertActionResult(digest: Digest, result: ActionResult) = actionCache.updateActionResult(updateActionResultRequest {
        actionDigest = digest
        actionResult = result
    }).voidify()

    override suspend fun checkHasMovable(digest: Digest): Path? {
        val result = tmpDir / digest.toFileName()
        result.collectFromFlow(read(digest) ?: return null)
        return result
    }

    override suspend fun writeFrom(digest: Digest, src: Path) {
        val inputFlow = tmpDir
            .createInnerTmpHardlinkTo(src)
            ?.inputStreamFlow(true, config.chunkSize)
            ?: return

        write(digest).collectFrom(inputFlow)
    }

    private fun readResourceName(digest: Digest)
        = "${config.toolName}/blobs/${digest.hash}/${digest.sizeBytes}"

    override suspend fun read(digest: Digest)
        = if (digest.hash != zeroHash)
            runCatching {
                val mappedFlow = bs
                    .read(readRequest { resourceName = readResourceName(digest) })
                    .map { it.data }

                mappedFlow.first()
                mappedFlow
            }.getOrNullIfNotFound()
        else
            flow { emit(ByteString.EMPTY) }

    private fun writeResourceName(digest: Digest)
        = "${config.toolName}/uploads/${UUID.randomUUID()}/blobs/${digest.hash}/${digest.sizeBytes}"

    @OptIn(FlowPreview::class)
    override fun write(digest: Digest): IWriter {
        if (writeEnabled == false) {
            throw Error("no writes possible due to server capabilities")
        }

        return ChannelWriter { f ->
            var first = true
            var offset = 0L
            bs.write(f.map {
                rateLimiter?.awaitQuota(it.size().toLong())
                try {
                    writeRequest {
                        if (first) {
                            first = false
                            resourceName = writeResourceName(digest)
                        }
                        writeOffset = offset
                        finishWrite = it.isEmpty
                        data = it
                    }
                } finally {
                    offset += it.size()
                }
            })
        }
    }

    override suspend fun findMissing(digests: List<Digest>): List<Digest> =
        cas.findMissingBlobs(findMissingBlobsRequest {
            blobDigests.addAll(digests)
        }).missingBlobDigestsList

    override suspend fun dispose() {}

}

fun createRemoteCache(
    directory: Path,
    channel: ManagedChannel,
    rpcConfig: RemoteCacheRpcConfig,
) = RemoteCache(
    directory,
    channel,
    rpcConfig,
)

fun createRemoteCache(
    directory: Path,
    config: RemoteCacheConfig,
) = createRemoteCache(
    directory,
    config.toManagedChannel(),
    config.rpcConfig,
)