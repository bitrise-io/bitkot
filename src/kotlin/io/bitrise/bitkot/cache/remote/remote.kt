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
import kotlinx.coroutines.channels.Channel
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

private class ErrorInitializedWriter(private val error: Throwable): IWriter {
    override suspend fun write(data: ByteString) = throw error
    override suspend fun write(data: ByteArray) = throw error
    override suspend fun commit() {}
    override fun close() {}
}

private open class ChannelWriter(consumer: suspend (Flow<ByteString>) -> Unit): IWriter {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channel = Channel<ByteString>(Channel.RENDEZVOUS)
    private val deferred = scope
        .async { consumer(channel.consumeAsFlow()) }

    override suspend fun write(data: ByteString)
        = channel.send(data)

    override suspend fun write(data: ByteArray)
        = channel.send(ByteString.copyFrom(data))

    override suspend fun commit() {
        close()
    }

    override fun close() {
        // actually, need to close the connection here which sends an EOF; which means "closing the channel"
        // because the kotlin grpc impl uses this channel interface, but this is NOT "closing the grpc channel"
        channel.close()
        deferred.cancel()
    }
}

private class ByteStreamWriter(
    logger: BitLogger,
    bs: ByteStreamGrpcKt.ByteStreamCoroutineStub,
    rateLimiter: RateLimiter?,
    digest: Digest,
    writeResourceName: String,
    private val deferredComplete: CompletableDeferred<Unit> = CompletableDeferred<Unit>()
): ChannelWriter({ f ->
    var first = true
    var offset = 0L
    val resp = bs.write(f.map {
        if ((offset + it.size()) > digest.sizeBytes)
            throw Error(
                "can't send write request for digest ${digest.toFileName()}, "+
                "next size will be ${offset + it.size()}, " +
                "but requested is ${digest.sizeBytes}"
            )

        rateLimiter?.awaitQuota(it.size().toLong())
        try {
            val req = writeRequest {
                if (first) {
                    first = false
                    resourceName = writeResourceName
                }
                writeOffset = offset
                finishWrite = (offset + it.size()) == digest.sizeBytes
                data = it
            }
            logger.debug(
                "sending write request for digest ${digest.toFileName()}:",
                "* offset - ${req.writeOffset}",
                "* size - ${req.data.size()}",
                "* finishWrite - ${req.finishWrite}",
            )
            req
        } finally {
            offset += it.size()
        }
    })
    logger.debug(
        "write end for digest ${digest.toFileName()}:",
        "* committed size - ${resp.committedSize}",
    )
    deferredComplete.complete(Unit)
}) {
    override suspend fun commit() {
        deferredComplete.await()
        super.commit()
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
    private val logger = createBitLogger()
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
            .inputStreamFlow(true, config.chunkSize)

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

    override fun write(digest: Digest): IWriter {
        if (digest.hash == zeroHash)
            return ErrorInitializedWriter(Error("no writes possible, hash is zero hash"))
        if (digest.sizeBytes <= 0)
            return ErrorInitializedWriter(Error("no writes possible, digest.sizeBytes has wrong value: ${digest.sizeBytes}"))

        return ByteStreamWriter(
            logger, bs, rateLimiter, digest,
            writeResourceName(digest),
        )
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