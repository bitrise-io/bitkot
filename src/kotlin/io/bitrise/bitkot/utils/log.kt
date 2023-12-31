package io.bitrise.bitkot.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.ref.WeakReference
import kotlin.math.log

abstract class BitLogger {
    abstract fun debug(d: () -> Any?)
    fun debug(vararg i: String) = debug { i.joinToString("\n") { it.trim() } }

    abstract fun error(d: () -> Any?)
    fun error(vararg i: String) = error { i.joinToString("\n") { it.trim() } }
    fun error(e: Throwable) = error { e }

    abstract fun warning(d: () -> Any?)
    fun warning(vararg i: String) = warning { i.joinToString("\n") { it.trim() } }

    abstract fun printConsole(d: () -> Any?)
    fun printConsole(vararg i: String) = printConsole { i.joinToString("\n") { it.trim() } }
}

fun (() -> Any?).toStringSafe(): String {
    return try {
        invoke().toString()
    } catch (e: Exception) {
        e.toString()
    }
}

typealias LoggerFactory = (BitLogging.PkgContext) -> BitLogger

object BitLogging {

    data class ByPackageLoggingProps(
        val props: Map<String, String>,
    )

    class ByPackageLoggingMutableProps {
        val props = mutableMapOf<String, String>()

        fun mergeDescending(other: ByPackageLoggingMutableProps) {
            for ((k,v) in other.props) {
               props[k] = v
            }
        }

        fun toImmutable() = ByPackageLoggingProps(
            props.toMap()
        )
    }

    private class Node {
        var props: ByPackageLoggingMutableProps? = null
        val nodes = HashMap<String, Node>()
    }

    private val root = Node()

    private val propsCache = mutableMapOf<String, ByPackageLoggingProps>()

    fun getProps(pkg: String): ByPackageLoggingProps = synchronized(BitLogging::class) {
        propsCache.computeIfAbsent(pkg) {
            val result = ByPackageLoggingMutableProps()
            var curNode = root
            for (part in pkg.split(".")) {
                curNode.props?.let { result.mergeDescending(it) }
                curNode = curNode.nodes[part] ?: break
            }
            result.toImmutable()
        }
    }

    fun editProps(pkg: String, editFn: ByPackageLoggingMutableProps.() -> Unit) = synchronized(BitLogging::class) {
        propsCache.clear()

        var curNode = root
        for (part in pkg.split(".")) {
            curNode = curNode.nodes.computeIfAbsent(part) { Node() }
        }
        if (curNode.props == null) {
            curNode.props = ByPackageLoggingMutableProps()
        }
        curNode.props?.let(editFn)
    }

    interface PkgContext {
        fun getProps(): ByPackageLoggingProps
        fun packageStr(): String
    }

    private var curLoggerFactory: LoggerFactory = {
        object: BitLogger() {
            val logger = KotlinLogging.logger(this.javaClass.name)
            override fun debug(d: () -> Any?) = logger.debug(d)
            override fun error(d: () -> Any?) = logger.error(d)
            override fun warning(d: () -> Any?) = logger.warn(d)
            override fun printConsole(d: () -> Any?) = println(d.toStringSafe())
        }
    }

    var loggerFactory: LoggerFactory
        get() = synchronized(BitLogging::class) { curLoggerFactory }
        set(value) = synchronized(BitLogging::class) { curLoggerFactory = value }
}

//private class PkgContextImpl(private val pkg: String): BitLogging.PkgContext {
//    private var propsRef: WeakReference<BitLogging.ByPackageLoggingProps>? = null
//
//    override fun packageStr() = pkg
//
//    override fun getProps(): BitLogging.ByPackageLoggingProps {
//        propsRef?.get()?.let { return it }
//        val result = BitLogging.getProps(pkg)
//        propsRef = WeakReference(result)
//        return result
//    }
//}

class LoggerWrapper(pkg: String): BitLogger() {
    private val logger by lazy { BitLogging.loggerFactory(object: BitLogging.PkgContext {
        override fun getProps() = BitLogging.getProps(pkg)
        override fun packageStr() = pkg
    }) }

    override fun debug(d: () -> Any?) = logger.debug(d)
    override fun error(d: () -> Any?) = logger.error(d)
    override fun warning(d: () -> Any?) = logger.warning(d)
    override fun printConsole(d: () -> Any?) = logger.printConsole(d)
}

fun <T: Any> T.createBitLogger(): BitLogger = LoggerWrapper(this.javaClass.name)
