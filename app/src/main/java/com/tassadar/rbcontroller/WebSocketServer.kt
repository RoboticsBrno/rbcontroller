package com.tassadar.rbcontroller

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress

class WebSocketServer(addr: InetSocketAddress, listener :OnWebSocketMessageListener) : WebSocketServer(addr) {
    companion object {
        const val tServer = "RBController:WSServer"
    }

    interface OnWebSocketMessageListener {
        fun onWebSocketMessage(message :String)
    }

    private val mListener = listener

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d(tServer, "onOpen: ${handshake?.resourceDescriptor} ${conn?.remoteSocketAddress?.address?.hostAddress}")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d(tServer, "onClose: ${conn?.remoteSocketAddress?.address?.hostAddress} $code $reason $remote")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        //Log.d(tServer, "onMessage: $message")

        if(message != null) {
            mListener.onWebSocketMessage(message)
        }
    }

    override fun onStart() {
        Log.d(tServer, "Started on ${this.address}")
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        ex?.printStackTrace()
    }

}
