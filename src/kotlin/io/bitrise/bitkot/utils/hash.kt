package io.bitrise.bitkot.utils

import java.io.FileInputStream
import java.nio.file.Path
import java.security.MessageDigest

fun ByteArray.toHexString()
    = joinToString(separator = "")
    { eachByte -> "%02x".format(eachByte) }

fun MessageDigest.toHexString() = digest().toHexString()

fun MessageDigest.updateGetSize(bytes: ByteArray):Long {
    update(bytes)
    return bytes.size.toLong()
}

fun MessageDigest.eat(file: Path): Int {
    //Get file input stream for reading the file content
    FileInputStream(file.toFile()).use { fis ->
        //Create byte array to read data in chunks
        val byteArray = ByteArray(1024)
        var bytesCount: Int
        var sizeBytes = 0

        //Read file data and update in message digest
        while (fis.read(byteArray).also { bytesCount = it } != -1) {
            sizeBytes += bytesCount
            this.update(byteArray, 0, bytesCount)
        }
        return sizeBytes
    }
}

