package com.tassadar.rbcontroller

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress

class WebSocketServer : WebSocketServer {
    val tServer = "RBController:WSServer"

    constructor(addr: InetSocketAddress) : super(addr)

    constructor(port: Int): super(InetSocketAddress(port))

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.i(tServer, "onOpen: ${handshake?.resourceDescriptor} ${conn?.remoteSocketAddress?.address?.hostAddress}")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.i(tServer, "onClose: ${conn?.remoteSocketAddress?.address?.hostAddress} $code $reason $remote")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.i(tServer, "onMessage: $message")
        conn?.send("Return: $message")
    }

    override fun onStart() {
        Log.i(tServer, "Started on ${this.address}")
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        ex?.printStackTrace()
    }

}
