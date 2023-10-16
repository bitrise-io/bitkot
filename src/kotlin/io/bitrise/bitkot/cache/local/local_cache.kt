package io.bitrise.bitkot.cache.local

import io.bitrise.bitkot.cache.iface.IWriter
import io.bitrise.bitkot.utils.createInnerTmpCopyTo
import io.bitrise.bitkot.utils.createInnerTmpHardlinkOrCopyTo
import io.bitrise.bitkot.utils.deleteDir
import io.bitrise.bitkot.utils.inputStreamFlow
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

interface IInnerLocalCache {
    suspend fun size(): Long
    suspend fun findMissing(db: String, keys: List<String>): List<String>

    suspend fun read(db: String, key: String): Flow<ByteString>?
    fun write(db: String, uuid: String, key: String): IWriter

    fun saveInto(db: String, key: String, src: Path)

    fun checkHasMovable(db: String, key: String): Path?
}

suspend fun IInnerLocalCache.readWhole(db: String, key: String): ByteString? {
    val readFlow = read(db, key)?: return null
    val data = mutableListOf<ByteString>()
    readFlow
        .onEach { data.add(it) }
        .collect()
    return ByteString.copyFrom(data)
}

suspend fun IInnerLocalCache.writeWhole(db: String, key: String, data: ByteString) {
    val writer = write(db, UUID.randomUUID().toString(), key)
    writer.write(data)
    writer.commit()
}


private open class Cache(
    protected val outDir: Path,
    protected val tmpDir: Path,
    protected val config: LocalCacheConfig,
): IInnerLocalCache {

    override suspend fun size(): Long =
        if (!outDir.exists()) 0L
        else outDir.toFile().walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()

    override suspend fun read(db: String, key: String)
        = checkHasMovable(db, key)?.inputStreamFlow(true, config.chunkSize)

    override fun checkHasMovable(db: String, key: String): Path? {
        val src = outDir.resolve(db).resolve(key)
        return if (config.useHardlinks)
            tmpDir.createInnerTmpHardlinkOrCopyTo(src)
        else
            tmpDir.createInnerTmpCopyTo(src)
    }


    open class Writer(
        private val path: Path,
        protected val dest: Path
    ): IWriter {
        private val stream = path.outputStream()

        override suspend fun write(data: ByteString) = withContext(Dispatchers.IO) {
            data.writeTo(stream)
        }

        override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
            stream.write(data)
        }

        override suspend fun commit() {
            if (!dest.parent.exists()) {
                dest.parent.toFile().mkdirs()
            }
            path.moveTo(dest, true)
            close()
        }

        override fun close() {
            stream.close()
            path.deleteIfExists()
        }

        protected fun finalize() {
            close()
        }
    }

    override fun write(db: String, uuid: String, key: String)
        = Writer(tmpDir.resolve(uuid), outDir.resolve(db).resolve(key))

    override fun saveInto(db: String, key: String, src: Path) {
        val dst = outDir.resolve(db).resolve(key)
        if (!dst.parent.exists()) {
            dst.parent.toFile().mkdirs()
        }

        if (!config.useHardlinks) {
            tmpDir.createInnerTmpCopyTo(src)?.moveTo(dst, true)
            return
        }

        dst.deleteIfExists()
        try {
            dst.createLinkPointingTo(src)
        } catch (e: Throwable) {
            src.copyTo(dst, true)
        }
    }

    override suspend fun findMissing(db: String, keys: List<String>)
        = keys.filter { !outDir.resolve(db).resolve(it).exists() }
}

private class LruCache(
    outDir: Path,
    tmpDir: Path,
    config: LocalCacheConfig
): Cache(outDir, tmpDir, config) {
    private val maxSize = (config.maxSizeMb ?: 50) * 1024 * 1024

    data class Node(val path: Path) {
        var next: Node? = null
        var prev: Node? = null
    }

    private var curSize: Long = 0
    private val map = hashMapOf<Path, Node>()
    private val head: Node = Node(outDir)
    private val tail: Node = Node(outDir)

    init {
        head.next = tail
        tail.prev = head
    }

    private fun addAtEnd(node: Node) {
        val prev = tail.prev!!
        prev.next = node
        node.prev = prev
        node.next = tail
        tail.prev = node
    }

    private fun remove(node: Node) {
        val next = node.next!!
        val prev = node.prev!!
        prev.next = next
        next.prev = prev
    }

    private fun checkLRU() {
        while(curSize > maxSize) {
            val first = head.next!!
            remove(first)
            val size = first.path.fileSize()
            first.path.deleteExisting()
            curSize -= size
            map.remove(first.path)
        }
    }

    private fun put(path: Path) {
        if (map.containsKey(path)) {
            remove(map[path]!!)
        } else {
            curSize += path.toFile().length()
        }
        val node = Node(path)
        addAtEnd(node)
        map[path] = node
    }

    private fun promote(path: Path) {
        val node = map[path]!!
        remove(node)
        addAtEnd(node)
    }

    fun onStartup() {
        if (!outDir.exists()) {
            return
        }
        val files = sortedMapOf<Long, MutableList<Path>>()
        outDir.toFile().walkTopDown()
            .filter { it.isFile }
            .forEach {
                val modified = it.lastModified()
                if (files[modified] != null) {
                    files[modified]!!.add(it.toPath())
                } else {
                    files[modified] = mutableListOf(it.toPath())
                }
            }

        files.values
            .flatten()
            .forEach {
                put(it)
            }

        checkLRU()
    }

    override fun checkHasMovable(db: String, key: String): Path? {
        val src = outDir
            .resolve(db)
            .apply { toFile().mkdirs() }
            .resolve(key)
        val dst = super.checkHasMovable(db, key) ?: return null
        synchronized(this) { promote(src) }
        return dst
    }

    class LruWriter(
        private val cache: LruCache,
        path: Path,
        dest: Path
    ): Writer(path, dest) {
        override suspend fun commit() {
            super.commit()
            synchronized(cache) {
                cache.put(dest)
                cache.checkLRU()
            }
        }
    }

    override fun write(db: String, uuid: String, key: String)
        = LruWriter(this, tmpDir.resolve(uuid), outDir.resolve(db).resolve(key))

    override fun saveInto(db: String, key: String, src: Path) {
        super.saveInto(db, key, src)
        synchronized(this) { put(outDir.resolve(db).resolve(key)) }
    }
}

fun createInnerLocalCache(directory: Path, config: LocalCacheConfig): IInnerLocalCache {
    directory.toFile().mkdirs()

    val outDir = directory / "cache"
    if (config.eraseOnStart && outDir.exists()) {
        outDir.deleteDir()
    }
    if (!outDir.exists() && !outDir.toFile().mkdirs()) {
        throw RuntimeException("Can't create dir: $outDir")
    }

    val tmpDir = directory / "tmp"
    if (tmpDir.exists()) {
        tmpDir.deleteDir()
    }
    if (!tmpDir.toFile().mkdirs()) {
        throw RuntimeException("Can't create dir: $tmpDir")
    }

    // If not max size defined - return regular cache
    config.maxSizeMb ?: return Cache(outDir, tmpDir, config)

    // Initialize LRU cache
    return LruCache(outDir, tmpDir, config)
        .apply { onStartup() }
}
