package com.tassadar.rbcontroller

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.util.concurrent.LinkedBlockingQueue

class UdpHandler(listener: OnUdpPacketListener) {
    companion object {
        const val LOG = "RB:UdpHandler"
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

    fun resetPort() = synchronized(this) {
        mBoundPort = 0
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun bindToNetwork(connMgr: ConnectivityManager, net: Network?) {
        Log.i(LOG, "Using network $net")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connMgr.bindProcessToNetwork(net);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(net);
        }
    }

    private fun newSocket(): DatagramSocket {
        return if(mBoundPort != 0) {
            DatagramSocket(InetSocketAddress(mBoundPort))
        } else {
            DatagramSocket()
        }
    }

    fun start(context: Context) = synchronized(this) {
        if(mStarted) {
            return
        }

        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connMgr.allNetworks.find { net ->
                val caps = connMgr.getNetworkCapabilities(net)
                if (caps == null)
                    false
                else if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    false
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND))
                    false
                else
                    true
            }.let { net ->
                bindToNetwork(connMgr, net)
            }
        }

        mSocket = try {
            newSocket()
        } catch(e :SocketException) {
            e.printStackTrace()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bindToNetwork(connMgr, null)
                newSocket()
            } else {
                throw e
            }
        }

        mSocket!!.broadcast = true
        mSocket!!.reuseAddress = true
        if(!mSocket!!.isBound) {
            mSocket!!.bind(null)
        }

        Log.i(LOG, "Bound to ${mSocket!!.localSocketAddress}")
        mBoundPort = mSocket!!.localPort

        mWriter = WriterThread(mSocket!!)
        mWriter?.start()

        mReader = ReaderThread(mSocket!!, mListener)
        mReader?.start()

        mStarted = true
    }

    fun stop() = synchronized(this) {
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

    private fun queuePacket(address: SocketAddress, data :JSONObject, isBroadcast :Boolean = false) = synchronized(this) {
        if(!isBroadcast) {
            data.put("n", mCounterWrite)
            ++mCounterWrite
        }
        val buf = data.toString().toByteArray();
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
            mSocket.soTimeout = 1
            while(!this.isInterrupted) {
                try {
                    sleep(16)
                } catch(ex :InterruptedException) {
                    return;
                }

                try {
                    mSocket.receive(pkt)

                    pkt.socketAddress

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
