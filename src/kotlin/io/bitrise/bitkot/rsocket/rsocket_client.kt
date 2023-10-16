package io.bitrise.bitkot.rsocket

import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.netty.tcp.TcpClient
import java.net.InetSocketAddress

fun createRsocketClient(endpoint: String, port: Int): RSocket {
    var client = TcpClient.create()
        .remoteAddress { InetSocketAddress(endpoint, port) }
    if (port == 443) {
        client = client.secure()
    }
    val transport = TcpClientTransport.create(client)
    return RSocketConnector.create()
        .fragment(16777215)
        .connect(transport)
        .block()!!
}