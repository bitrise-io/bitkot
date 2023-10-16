package io.bitrise.bitkot.domain_socket

import io.bitrise.bitkot.utils.Disposable
import io.bitrise.bitkot.utils.suspendAwait
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.ServerInterceptor
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultithreadEventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.unix.DomainSocketAddress

private fun<T> withUnsupportedException(): T {
    throw RuntimeException("Unsupported OS '${System.getProperty("os.name") }', only Unix and Mac are supported")
}

open class DomainSocketBuilderBase {
    private val evGroups = mutableListOf<MultithreadEventLoopGroup>()

    protected fun eventLoopGroup(threads: Int = 0): EventLoopGroup =
        if (Epoll.isAvailable())
            EpollEventLoopGroup(threads).apply(evGroups::add)
        else if (KQueue.isAvailable())
            KQueueEventLoopGroup(threads).apply(evGroups::add)
        else
            withUnsupportedException()

    protected suspend fun shutdownEventGroups() {
        evGroups.map { it.shutdownGracefully().suspendAwait() }
    }
}

class NettyDomainSocketServerBuilder(socket: String): DomainSocketBuilderBase() {
    companion object {
        fun forDomainSocket(socket: String) = NettyDomainSocketServerBuilder(socket)
    }

    private var innerBuilder = NettyServerBuilder
        .forAddress(DomainSocketAddress(socket))
//        .withChildOption(ChannelOption.SO_KEEPALIVE, false)
        .channelType(
            if (Epoll.isAvailable())
                EpollServerDomainSocketChannel::class.java
            else if (KQueue.isAvailable())
                KQueueServerDomainSocketChannel::class.java
            else
                withUnsupportedException()
        )

    private fun wrapInner(block: NettyServerBuilder.() -> NettyServerBuilder): NettyDomainSocketServerBuilder {
        block(innerBuilder)
        return this
    }

    private val evGroups = mutableListOf<MultithreadEventLoopGroup>()

    fun eventGroups(boss: Int, worker: Int) = wrapInner {
        this.bossEventLoopGroup(eventLoopGroup(boss))
            .workerEventLoopGroup(eventLoopGroup(worker))
    }

    fun addService(bindableService: BindableService?) = wrapInner { addService(bindableService) }
    fun intercept(interceptor: ServerInterceptor) = wrapInner { this.intercept(interceptor) }

    fun build(): Pair<Server, Disposable> {
        val server = innerBuilder.build()
        return Pair(server, Disposable {
            server.shutdown()
            shutdownEventGroups()
        })
    }
}

class NettyDomainSocketChannelBuilder(socket: String): DomainSocketBuilderBase() {
    companion object {
        fun forDomainSocket(socket: String) = NettyDomainSocketChannelBuilder(socket)
    }

    private var innerBuilder = NettyChannelBuilder
        .forAddress(DomainSocketAddress(socket))
//        .withOption(ChannelOption.SO_KEEPALIVE, false)
        .eventLoopGroup(eventLoopGroup())
        .channelType(
            if (Epoll.isAvailable())
                EpollDomainSocketChannel::class.java
            else if (KQueue.isAvailable())
                KQueueDomainSocketChannel::class.java
            else
                withUnsupportedException()
        )


    private fun wrapInner(block: NettyChannelBuilder.() -> NettyChannelBuilder): NettyDomainSocketChannelBuilder {
        block(innerBuilder)
        return this
    }

    fun usePlainText() = wrapInner { usePlaintext() }

    fun build(): Pair<ManagedChannel, Disposable> {
        return Pair(innerBuilder.build(), Disposable { shutdownEventGroups() })
    }
}