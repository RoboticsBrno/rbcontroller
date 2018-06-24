package com.tassadar.rbcontroller

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.util.concurrent.LinkedBlockingQueue

class UdpHandler(listener: OnUdpPacketListener) {
    companion object {
        const val LOG = "RBController:UdpHandler"
        const val BROADCAST_PORT = 42424

        const val CMD_DISCOVER = "discover"

        const val CMD_FOUND = "found"
    }

    interface OnUdpPacketListener {
        fun onUdpPacket(addr :InetSocketAddress, cmd :String, data :JSONObject)
    }

    var mSocket: DatagramSocket? = null
    var mWriter: WriterThread? = null
    var mReader: ReaderThread? = null
    var mStarted = false
    val mListener = listener

    @Synchronized
    fun start() {
        if(!mStarted) {
            mSocket = DatagramSocket(null)
            mSocket!!.broadcast = true
            mSocket!!.reuseAddress = true
            mSocket!!.bind(null)

            Log.i(LOG, "Bound to ${mSocket!!.localSocketAddress}")

            mWriter = WriterThread(mSocket!!)
            mWriter?.start()

            mReader = ReaderThread(mSocket!!, mListener)
            mReader?.start()

            mStarted = true
        }
    }

    @Synchronized
    fun stop() {
        if(mStarted) {
            mWriter?.interrupt()
            mWriter?.join()
            mWriter = null

            mReader?.interrupt()
            mReader?.join()
            mReader = null

            mSocket!!.close()
            mSocket = null

            mStarted = false
        }
    }

    @Synchronized
    private fun queuePacket(pkt :DatagramPacket) {
        mWriter?.queuePacket(pkt)
    }

    fun send(address: SocketAddress, command: String, params :JSONObject? = null) {
        val data = if(params == null) {
            """{"c":"$command"}""".toByteArray()
        } else {
            params.put("c", command)
            params.toString().toByteArray()
        }

        queuePacket(DatagramPacket(data, data.size, address))
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

    class ReaderThread(socket: DatagramSocket, listener :OnUdpPacketListener) : Thread() {
        private val mSocket = socket
        private val mListener = listener

        override fun run() {
            val buf = ByteArray(65535)
            val pkt = DatagramPacket(buf, buf.size)
            while(!this.isInterrupted) {
                mSocket.soTimeout = 250
                try {
                    mSocket.receive(pkt)

                    val str = String(pkt.data, pkt.offset, pkt.length)
                    Log.i(UdpHandler.LOG, "Got packet from ${pkt.socketAddress}: ${String(pkt.data, pkt.offset, pkt.length)}")

                    try {
                        val data = JSONObject(str)
                        val cmd = data.getString("c")
                        data.remove("c")
                        mListener.onUdpPacket(InetSocketAddress(pkt.address, pkt.port), cmd, data)
                    } catch(ex :Exception) {
                        Log.e(LOG, "Exception while handling data from ${pkt.socketAddress}!", ex)
                    }
                } catch(ex :SocketTimeoutException) {
                    // pass, check for interrupt
                }
            }
        }
    }
}
