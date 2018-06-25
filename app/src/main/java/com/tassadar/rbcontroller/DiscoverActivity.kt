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
import android.R.menu
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem


class DiscoverActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener, DiscoverAdapter.OnDeviceClickedListener {
    private val mUdpHandler = UdpHandler(this)
    private var mScanning = false
    private var mScanTimedOut = false
    private var mTimer :Timer? = null
    private var mScanningTask :TimerTask? = null
    private val mDevices = ArrayList<Device>()
    private val mDiscoveredAddresses = HashSet<String>()
    private val mAdapter = DiscoverAdapter(mDevices, this)
    private var mLastDeviceName = ""
    private var mLastDeviceHost = ""
    private var mActivityStartTime :Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val manager = LinearLayoutManager(this)

        findViewById<RecyclerView>(R.id.device_list).apply {
            setHasFixedSize(true)
            layoutManager = manager
            adapter = mAdapter
        }

        val pref = getSharedPreferences("", MODE_PRIVATE)
        mLastDeviceName = pref.getString("lastDeviceName", "")
        mLastDeviceHost = pref.getString("lastDeviceHost", "")
        mActivityStartTime = System.currentTimeMillis()

        mUdpHandler.start()
        if(savedInstanceState == null) {
            startScan()
        } else if(savedInstanceState.containsKey("devices")) {
            if(savedInstanceState.getBoolean("scanning", true))
                startScan()
            else
                mScanTimedOut = true

            val savedDevices = savedInstanceState.getSerializable("devices") as ArrayList<Device>
            if(savedDevices.size != 0) {
                val prevSize = mDevices.size
                mDevices.addAll(savedDevices)
                savedDevices.forEach {
                    mDiscoveredAddresses.add(it.address.hostString)
                }
                mAdapter.notifyItemRangeInserted(prevSize, savedDevices.size)
                setNoDevicesVisible(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mUdpHandler.start()
    }

    override fun onPause() {
        super.onPause()

        mTimer?.cancel()
        mTimer = null
        mScanning = false

        mUdpHandler.stop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putSerializable("devices", mDevices)
        outState.putBoolean("scanning", !mScanTimedOut)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.activity_discover, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                startScan()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 0) {
            mLastDeviceHost = ""
            mLastDeviceName = ""

            val editor = getSharedPreferences("", MODE_PRIVATE).edit()
            editor.remove("lastDeviceName")
            editor.remove("lastDeviceHost")
            editor.apply()
        }
    }

    private fun startScan() {
        if(mScanning) {
            return
        }
        mScanning = true
        mScanTimedOut = false

        mTimer = Timer()
        mScanningTask = timerTask{
            mUdpHandler.broadcast(UdpHandler.CMD_DISCOVER)
        }
        mTimer?.schedule(mScanningTask, 0, 5000)
        mTimer?.schedule(timerTask {
            stopScanning()
        }, 15000)

        val size = mDevices.size
        if(size != 0) {
            mDevices.clear()
            mDiscoveredAddresses.clear()
            mAdapter.notifyItemRangeRemoved(0, size)
        }

        setStatus(R.string.scanning)
        setNoDevicesVisible(true)
    }

    private fun stopScanning() {
        runOnUiThread {
            mScanningTask?.cancel()
            mScanning = false
            mScanTimedOut = true
            setStatus(0)
        }
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

            if(System.currentTimeMillis() - mActivityStartTime < 500 &&
                    mLastDeviceName == dev.name && mLastDeviceHost == dev.address.hostString) {
                onDeviceClicked(dev)
            }

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
        mLastDeviceName = dev.name
        mLastDeviceHost = dev.address.hostString

        val editor = getSharedPreferences("", MODE_PRIVATE).edit()
        editor.putString("lastDeviceName", mLastDeviceName)
        editor.putString("lastDeviceHost", mLastDeviceHost)
        editor.apply()

        val intent = Intent(this, ControllerActivity::class.java)
        intent.putExtra("device", dev)
        startActivityForResult(intent, 0)

        stopScanning()
    }
}