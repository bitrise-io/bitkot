package bitkot.utils

import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.io.path.copyTo
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.getPosixFilePermissions

fun makePath(strPath: String): Path {
    if (strPath.startsWith("~")) {
        return Path.of(System.getProperty("user.home") + strPath.substring(1))
    }
    return Path.of(strPath)
}

private fun createInnerTmpHardlinkToDetail(src: Path, dst: Path): Path? {
    try {
        dst.createLinkPointingTo(src)
    } catch (_: Throwable) {
        return null
    }
    return dst
}

fun Path.createInnerTmpHardlinkTo(src: Path)
    = createInnerTmpHardlinkToDetail(src, resolve(UUID.randomUUID().toString()))

private fun createInnerTmpCopyToDetail(src: Path, dst: Path): Path? {
    try {
        src.copyTo(dst)
    } catch (_: Throwable) {
        return null
    }
    return dst
}

fun Path.createInnerTmpCopyTo(src: Path): Path? {
    val dst = resolve(UUID.randomUUID().toString())
    return createInnerTmpCopyToDetail(src, dst)
}

fun Path.createInnerTmpHardlinkOrCopyTo(src: Path): Path? {
    val dst = resolve(UUID.randomUUID().toString())
    return createInnerTmpHardlinkToDetail(src, dst)
        ?: createInnerTmpCopyToDetail(src, dst)
}

fun cwd(): Path = Paths.get("").toAbsolutePath()

private val kPermissionBits = arrayOf(
    PosixFilePermission.OTHERS_EXECUTE,
    PosixFilePermission.OTHERS_WRITE,
    PosixFilePermission.OTHERS_READ,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_READ
)

fun modeToPosixPerms(mode: Int): Set<PosixFilePermission> {
    var mask = 1
    val result = mutableSetOf<PosixFilePermission>()
    for (flag in kPermissionBits) {
        if (mask and mode != 0) {
            result.add(flag)
        }
        mask = mask shl 1
    }
    return result
}

fun Path.getFileMode(vararg options: LinkOption): Int {
    val posixPermissions = getPosixFilePermissions(*options)
    var mode = 0
    for (i in kPermissionBits.indices)
        if (posixPermissions.contains(kPermissionBits.get(i)))
            mode += 1 shl i
    return mode
}

// yes java cannot into directory deletion ¯\_(ツ)_/¯
fun Path.deleteDir() = Runtime
    .getRuntime()
    .exec(arrayOf("rm", "-rf", this.toString()))
    .waitFor() == 0
