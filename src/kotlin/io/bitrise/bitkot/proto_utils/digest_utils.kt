package io.bitrise.bitkot.proto_utils

import io.bitrise.bitkot.utils.eat
import io.bitrise.bitkot.utils.toHexString
import build.bazel.remote.execution.v2.Digest
import build.bazel.remote.execution.v2.digest
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readSymbolicLink

fun Path.calculateDigest(): Digest {
    if (isSymbolicLink())
        return readSymbolicLink().toString().calculateDigest()

    val shaDigest = MessageDigest.getInstance("SHA-256")
    val size = shaDigest.eat(this)
    return digest {
        hash = shaDigest.toHexString()
        sizeBytes = size.toLong()
    }
}

fun ByteArray.calculateDigest(): Digest {
    val ba = this
    return digest {
        hash = MessageDigest.getInstance("SHA-256")
            .digest(ba)
            .toHexString()
        sizeBytes = ba.size.toLong()
    }
}

fun String.calculateDigest() = encodeToByteArray()
    .calculateDigest()

fun Digest.toFileName() = "$hash:$sizeBytes"

fun parseDigestFromFilename(fileName: String): Digest {
    val split = fileName.split(":")
    if (split.size != 2) {
        throw InvalidPathException(fileName, "does not contain :")
    }
    return digest {
        hash = split[0]
        sizeBytes = split[1].toLong()
    }
}