package com.tassadar.rbcontroller

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.util.concurrent.LinkedBlockingQueue

class UdpHandler {
    companion object {
        const val LOG = "RBController:UdpHandler"
        const val BROADCAST_PORT = 42424
    }

    val mSocket: DatagramSocket = DatagramSocket(null)
    var mWriter: WriterThread? = null
    var mReader: ReaderThread? = null
    var mStarted = false

    constructor() {
        mSocket.reuseAddress = true
        mSocket.broadcast = true
    }

    @Synchronized
    fun start() {
        if(!mStarted) {
            mSocket.bind(null)

            Log.i(LOG, "Bound to ${mSocket.localSocketAddress}")

            mWriter = WriterThread(mSocket)
            mWriter?.start()

            mReader = ReaderThread(mSocket)
            mReader?.start()

            mStarted = true
        }
    }

    @Synchronized
    fun stop() {
        if(mStarted) {
            mWriter?.interrupt()
            mWriter?.join()

            mReader?.interrupt()
            mReader?.join()

            mSocket.close()

            mStarted = false
        }
    }

    fun send(address: SocketAddress, command: String, params :JSONObject? = null) {
        val data = if(params == null) {
            """{"c":"$command"}""".toByteArray()
        } else {
            params.put("c", command)
            params.toString().toByteArray()
        }

        mWriter?.queuePacket(DatagramPacket(data, data.size, address))
    }

    fun broadcast(command: String, params :JSONObject? = null) {
        send(InetSocketAddress("255.255.255.255", BROADCAST_PORT), command, params)
    }

    class WriterThread(socket: DatagramSocket) : Thread() {
        private val mSocket :DatagramSocket = socket
        private val mQueue = LinkedBlockingQueue<DatagramPacket>()

        fun queuePacket(pkt :DatagramPacket) {
            mQueue.put(pkt)
        }

        override fun run() {
            while(!this.isInterrupted) {
                try {
                    val pkt = mQueue.take()
                    mSocket.send(pkt)
                } catch(ex :InterruptedException) {
                    return
                } catch(ex :IOException) {
                    ex.printStackTrace()
                }
            }
        }
    }

    class ReaderThread(socket: DatagramSocket) : Thread() {
        private val mSocket :DatagramSocket = socket

        override fun run() {
            val buf = ByteArray(65535)
            val pkt = DatagramPacket(buf, buf.size)
            while(!this.isInterrupted) {
                mSocket.soTimeout = 500
                try {
                    mSocket.receive(pkt)
                    Log.i(UdpHandler.LOG, "Got packet from ${pkt.socketAddress}: ${String(pkt.data, pkt.offset, pkt.length)}")
                } catch(ex :SocketTimeoutException) {
                    // pass, check for interrupt
                }
            }
        }
    }
}
