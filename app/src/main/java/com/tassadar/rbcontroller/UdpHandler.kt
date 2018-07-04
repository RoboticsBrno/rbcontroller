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

    private var mSocket: DatagramSocket? = null
    private var mWriter: WriterThread? = null
    private var mReader: ReaderThread? = null
    private var mStarted = false
    private val mListener = listener
    private var mCounterWrite = 0
    private var mBoundPort = 0

    @Synchronized
    fun start() {
        if(!mStarted) {
            mSocket = DatagramSocket(null)
            mSocket!!.broadcast = true
            mSocket!!.reuseAddress = true
            if(mBoundPort == 0) {
                mSocket!!.bind(null)
                mBoundPort = mSocket!!.localPort
            } else {
                mSocket!!.bind(InetSocketAddress(mBoundPort))
            }

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
    private fun queuePacket(address: SocketAddress, data :JSONObject, isBroadcast :Boolean = false) {
        if(!isBroadcast) {
            data.put("n", mCounterWrite)
            ++mCounterWrite
        }
        val buf = data.toString().toByteArray()
        mWriter?.queuePacket(DatagramPacket(buf, buf.size, address))
    }

    fun send(address: SocketAddress, command: String, params :JSONObject? = null, isBroadcast :Boolean = false) {
        var data = params
        if(data == null) {
            data = JSONObject()
        }
        data.put("c", command)
        queuePacket(address, data, isBroadcast)
    }

    fun broadcast(command: String, params :JSONObject? = null) {
        send(InetSocketAddress("255.255.255.255", BROADCAST_PORT), command, params, true)
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
        private val mCounters = HashMap<InetAddress, Int>()

        override fun run() {
            val buf = ByteArray(65535)
            val pkt = DatagramPacket(buf, buf.size)
            while(!this.isInterrupted) {
                mSocket.soTimeout = 250
                try {
                    mSocket.receive(pkt)

                    val str = String(pkt.data, pkt.offset, pkt.length)
                    //Log.d(UdpHandler.LOG, "Got packet from ${pkt.socketAddress}: ${String(pkt.data, pkt.offset, pkt.length)}")

                    try {
                        val data = JSONObject(str)

                        // Ignore out of order packets
                        val n = data.optInt("n", 0)
                        val diff = mCounters.getOrElse(pkt.address) { n } - n
                        if (diff > 0 && diff < 300) {
                            continue
                        }
                        mCounters[pkt.address] = n

                        val cmd = data.getString("c")
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
