package com.tassadar.rbcontroller

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.*
import kotlin.concurrent.timerTask
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText


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

    companion object {
        const val ACT_CONTROLLER = 0
        const val ACT_ONBOARD = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val manager = LinearLayoutManager(this)

        findViewById<RecyclerView>(R.id.device_list).apply {
            itemAnimator = null
            setHasFixedSize(true)
            layoutManager = manager
            adapter = mAdapter
        }

        val pref = getSharedPreferences("", Context.MODE_PRIVATE)
        mAdapter.showOtherPeoplesDevices = pref.getBoolean("showOtherPeoplesDevices", false)
        if(!pref.contains("owner")) {
            startActivityForResult(Intent(this, OnboardActivity::class.java), ACT_ONBOARD)
        } else {
            mAdapter.owner = pref.getString("owner", "")
            init(savedInstanceState)
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

        menu.findItem(R.id.other_peoples_devices).isChecked = mAdapter.showOtherPeoplesDevices
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                startScan()
                return true
            }
            R.id.change_owner -> {
                showRenameDialog()
                return true
            }
            R.id.other_peoples_devices -> {
                mAdapter.showOtherPeoplesDevices = !item.isChecked
                item.isChecked = mAdapter.showOtherPeoplesDevices

                val e = getSharedPreferences("", Context.MODE_PRIVATE).edit()
                e.putBoolean("showOtherPeoplesDevices", mAdapter.showOtherPeoplesDevices)
                e.apply()

                mAdapter.notifyDataSetChanged()
                setNoDevicesVisible(mAdapter.itemCount == 0)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            ACT_CONTROLLER -> {
                mLastDeviceHost = ""
                mLastDeviceName = ""

                val editor = getSharedPreferences("", MODE_PRIVATE).edit()
                editor.remove("lastDeviceName")
                editor.remove("lastDeviceHost")
                editor.apply()
            }
            ACT_ONBOARD -> {
                if(resultCode == Activity.RESULT_OK) {
                    mAdapter.owner = data!!.getStringExtra("owner")
                    init(null)
                } else {
                    finish()
                }
            }
        }
    }

    private fun init(savedInstanceState :Bundle?) {
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
        mTimer?.schedule(mScanningTask, 0, 200)
        mTimer?.schedule(timerTask {
            stopScanning()
        }, 10000)

        val size = mDevices.size
        if(size != 0) {
            mDevices.clear()
            mDiscoveredAddresses.clear()
            mAdapter.notifyItemRangeRemoved(0, size)
        }

        setStatus(getString(R.string.scanning, mAdapter.owner))
        setNoDevicesVisible(true)
    }

    private fun stopScanning() {
        runOnUiThread {
            mScanningTask?.cancel()
            mScanning = false
            mScanTimedOut = true
            setStatus(null)
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
                    data.getString("owner"),
                    data.getString("name"),
                    data.getString("desc"),
                    data.optString("path", "/index.html"),
                    data.optInt("port", 80))

            if(System.currentTimeMillis() - mActivityStartTime < 500 &&
                    mLastDeviceName == dev.name && mLastDeviceHost == dev.address.hostString) {
                onDeviceClicked(dev)
            }

            var pos = Collections.binarySearch(mDevices, dev) { a, b ->
                val aOwner = a.owner == mAdapter.owner
                val bOwner = b.owner == mAdapter.owner
                if(aOwner && !bOwner) {
                    return@binarySearch -1
                }else if (!aOwner && !bOwner) {
                    return@binarySearch 1
                } else {
                    return@binarySearch a.name.compareTo(b.name)
                }
            }
            if(pos < 0)
                pos = -pos-1

            mAdapter.addDevice(pos, dev)
            if(mAdapter.itemCount != 0) {
                setNoDevicesVisible(false)
            }
        }
    }

    private fun setStatus(id :Int) {
        setStatus(getString(id))
    }

    private fun setStatus(text :String?) {
        val l = findViewById<LinearLayout>(R.id.status_layout)
        if(text != null) {
            findViewById<TextView>(R.id.status_text).setText(text)
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
        startActivityForResult(intent, ACT_CONTROLLER)

        stopScanning()
    }

    private fun showRenameDialog() {
        val builder = AlertDialog.Builder(this)
        val layout = View.inflate(this, R.layout.dialog_edittext, null);
        val ed = layout.findViewById<EditText>(R.id.edittext);
        ed.setText(mAdapter.owner)
        ed.setHint(R.string.owner_name)
        ed.filters = arrayOf(AsciiInputFilter())

        builder.setCancelable(true)
                .setView(layout)
                .setTitle(R.string.change_owner)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    mAdapter.owner = ed.text.toString()

                    val e = getSharedPreferences("", Context.MODE_PRIVATE).edit()
                    e.putString("owner", mAdapter.owner)
                    e.apply()

                    stopScanning()
                    startScan()
                }
        val dialog = builder.create()
        dialog.show()

        val btnOk = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btnOk.isEnabled = ed.text.length in 3..30
        ed.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                btnOk.isEnabled = text!!.length in 3..30
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    }
}
