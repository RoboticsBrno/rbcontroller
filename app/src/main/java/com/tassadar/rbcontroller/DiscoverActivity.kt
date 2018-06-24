package com.tassadar.rbcontroller

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.*
import kotlin.concurrent.timerTask


class DiscoverActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener, DiscoverAdapter.OnDeviceClickedListener {
    private val mUdpHandler = UdpHandler(this)
    private var mTimer :Timer? = null
    private val mDevices = ArrayList<Device>()
    private val mDiscoveredAddresses = HashSet<String>()
    private val mAdapter = DiscoverAdapter(mDevices, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val manager = LinearLayoutManager(this)

        findViewById<RecyclerView>(R.id.device_list).apply {
            setHasFixedSize(true)
            layoutManager = manager
            adapter = mAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        mUdpHandler.start()

        startScan()
    }

    override fun onPause() {
        super.onPause()

        mTimer?.cancel()
        mTimer = null

        mUdpHandler.stop()
    }

    private fun startScan() {
        mTimer = Timer()
        val task = timerTask{
            mUdpHandler.broadcast(UdpHandler.CMD_DISCOVER)
        }
        mTimer?.schedule(task, 0, 7000)
        mTimer?.schedule(timerTask {
            task.cancel()
            runOnUiThread { setStatus(0) }
        }, 15000)

        val size = mDevices.size
        mDevices.clear()
        mDiscoveredAddresses.clear()
        mAdapter.notifyItemRangeRemoved(0, size)

        setStatus(R.string.scanning)
        setNoDevicesVisible(true)
    }

    override fun onUdpPacket(addr :InetSocketAddress, cmd :String, data :JSONObject) {
        if(cmd != UdpHandler.CMD_FOUND)
            return

        runOnUiThread {
            if(mDiscoveredAddresses.contains(addr.hostString))
                return@runOnUiThread
            mDiscoveredAddresses.add(addr.hostString)

            val dev = Device(addr,
                    data.getString("name"),
                    data.getString("desc"),
                    data.optString("path", "/"),
                    data.optInt("port", 80))

            val pos = mDevices.size
            mDevices.add(dev)
            mAdapter.notifyItemInserted(pos)
            if(pos == 0) {
                setNoDevicesVisible(false)
            }
        }
    }

    private fun setStatus(id :Int) {
        val l = findViewById<LinearLayout>(R.id.status_layout)
        if(id != 0) {
            findViewById<TextView>(R.id.status_text).setText(id)
            l.visibility = View.VISIBLE
        } else {
            l.visibility = View.GONE
        }
    }

    private fun setNoDevicesVisible(visible :Boolean) {
        findViewById<TextView>(R.id.no_devices_text)
                .visibility = if(visible) View.VISIBLE else View.GONE
    }

    override fun onDeviceClicked(dev: Device) {
        val intent = Intent(this, ControllerActivity::class.java)
        intent.putExtra("device", dev)
        startActivity(intent)
    }
}