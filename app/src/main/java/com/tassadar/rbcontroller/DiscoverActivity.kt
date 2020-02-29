package com.tassadar.rbcontroller

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.tassadar.rbcontroller.ble.BleManager
import com.tassadar.rbcontroller.onboard.OnboardActivity
import com.tassadar.rbcontroller.wifi.WifiConfigList
import com.tassadar.rbcontroller.wifi.WifiConnector
import org.json.JSONObject
import java.io.Closeable
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.timerTask


class DiscoverActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener, DiscoverAdapter.OnDeviceClickedListener {
    private val mUdpHandler = UdpHandler(this)
    private var mScanning = false
    private var mScanTimedOut = false
    private var mTimer :Timer? = null
    private val mDiscoveredWiFi = HashSet<String>()
    private var mLastDeviceName = ""
    private var mLastDeviceHost = ""
    private var mActivityStartTime :Long = 0
    private var mMenuItemShowOthers :MenuItem? = null
    private val mBtManager = BleManager()
    private var mWifiConnector: Closeable? = null

    private val mDevices: DeviceRegistry  by lazy(LazyThreadSafetyMode.NONE) {
        DeviceRegistry(this, packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
    }

    companion object {
        const val ACT_CONTROLLER = 0
        const val ACT_ONBOARD = 1
        const val ACT_WIFI_CONFIG = 2

        const val LOG = "RB:DiscoverAct"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val manager = LinearLayoutManager(this)
        findViewById<RecyclerView>(R.id.device_list).apply {
            itemAnimator = null
            setHasFixedSize(true)
            layoutManager = manager
            adapter = mDevices.getAdapter()
        }

        val pref = getSharedPreferences("", Context.MODE_PRIVATE)
        mDevices.setShowOtherPeoplesDevices(pref.getBoolean("showOtherPeoplesDevices2", false))

        if(!pref.contains("owner") || shouldShowBleOnboard()) {
            startActivityForResult(Intent(this, OnboardActivity::class.java), ACT_ONBOARD)
        } else {
            mDevices.setOwner( pref.getString("owner", "")!!)
            init(savedInstanceState)
        }
    }

    private fun shouldShowBleOnboard(): Boolean {
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return false

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return false

        return !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        mWifiConnector?.close()
        mWifiConnector = null
    }

    override fun onPause() {
        super.onPause()

        stopScanning(false)
        mTimer?.cancel()
        mTimer = null
    }

    override fun onStop() {
        super.onStop()
        mBtManager.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("scanning", !mScanTimedOut)
        mDevices.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_discover, menu)

        mMenuItemShowOthers = menu.findItem(R.id.other_peoples_devices)
        mMenuItemShowOthers!!.isChecked = mDevices.getShowOtherPeoplesDevices()
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
                if(item.isChecked) {
                    setShowOtherPeoplesDevices(!item.isChecked)
                } else {
                    showPassDialog()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun setShowOtherPeoplesDevices(show: Boolean) {
        mDevices.setShowOtherPeoplesDevices(show)
        mMenuItemShowOthers!!.isChecked = show

        val e = getSharedPreferences("", Context.MODE_PRIVATE).edit()
        e.putBoolean("showOtherPeoplesDevices2", show)
        e.apply()

        setNoDevicesVisible(mDevices.getVisible().isEmpty())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            ACT_CONTROLLER -> {
                WifiConnector.releaseNetwork(this)

                mLastDeviceHost = ""
                mLastDeviceName = ""

                val editor = getSharedPreferences("", MODE_PRIVATE).edit()
                editor.remove("lastDeviceName")
                editor.remove("lastDeviceHost")
                editor.apply()
            }
            ACT_ONBOARD -> {
                if(resultCode == Activity.RESULT_OK) {
                    val owner = getSharedPreferences("", Context.MODE_PRIVATE).getString("owner", "")!!
                    mDevices.setOwner(owner)
                    init(null)
                } else {
                    finish()
                }
            }
            ACT_WIFI_CONFIG -> {
                if(resultCode == Activity.RESULT_OK) {
                    startScan()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResult: IntArray) {
        if(grantResult.isNotEmpty() && grantResult[0] == PackageManager.PERMISSION_GRANTED) {
            stopScanning()
            startScan()
        }
    }

    @UiThread
    private fun init(savedInstanceState :Bundle?) {
        val pref = getSharedPreferences("", MODE_PRIVATE)
        mLastDeviceName = pref.getString("lastDeviceName", "")!!
        mLastDeviceHost = pref.getString("lastDeviceHost", "")!!
        mActivityStartTime = System.currentTimeMillis()

        mBtManager.init(this)

        if(savedInstanceState == null || !savedInstanceState.containsKey("devices")) {
            startScan()
        } else {
            mDevices.loadSavedInstance(savedInstanceState)
            mDevices.getAll()
                    .filter{ it.wifi != null }
                    .forEach {
                        mDiscoveredWiFi.add(it.wifi!!.address.hostAddress)
                    }
            setNoDevicesVisible(mDevices.getVisible().isEmpty())

            mScanTimedOut = !savedInstanceState.getBoolean("scanning", true)
            if(!mScanTimedOut)
                startScan()
        }
    }

    @UiThread
    private fun startScan() {
        if(mScanning) {
            return
        }
        mScanning = true
        mScanTimedOut = false

        mUdpHandler.start(this)

        mTimer = Timer()
        mTimer?.schedule(timerTask{
            mUdpHandler.broadcast(UdpHandler.CMD_DISCOVER)
        }, 0, 200)

        mTimer?.schedule(timerTask {
            stopScanning()
        }, 15000)

        mDevices.clear()
        mDiscoveredWiFi.clear()

        mBtManager.startScan(this)

        setStatus(getString(R.string.scanning, mDevices.getOwner()))
        setNoDevicesVisible(true)

        //onBleDeviceDiscovered(Device.Ble("A4:CF12:24:C9:CE", InetAddress.getByName("192.168.0.1"), "FrantaFlinta", "SuperRuka", 100,
        //Device.WifiConfig(false, "", "flusflus", 1)))
    }

    private fun stopScanning() = stopScanning(true)
    private fun stopScanning(timedOut: Boolean) {
        runOnUiThread {
            if(!mScanning)
                return@runOnUiThread

            mUdpHandler.stop()

            mTimer?.cancel()
            mTimer = null

            mScanning = false
            mScanTimedOut = timedOut
            mBtManager.stopScanning()
            setStatus(null)
        }
    }

    override fun onUdpPacket(addr :InetSocketAddress, cmd :String, data :JSONObject) {
        if(cmd != UdpHandler.CMD_FOUND)
            return

        runOnUiThread {
            if(mDiscoveredWiFi.contains(addr.address.hostAddress))
                return@runOnUiThread
            mDiscoveredWiFi.add(addr.address.hostAddress)

            val wifi = Device.WiFi(addr.address,
                    data.getString("owner"),
                    data.getString("name"),
                    data.getString("desc"),
                    data.optString("path", "/index.html"),
                    data.optInt("port", 80))

            mDevices.add(wifi)
            setNoDevicesVisible(mDevices.getVisible().isEmpty())

            if(System.currentTimeMillis() - mActivityStartTime < 500 &&
                    mLastDeviceName == wifi.name && mLastDeviceHost == wifi.address.hostAddress) {
                startControllerActivity(wifi)
            }
        }
    }

    fun onBleDeviceDiscovered(ble :Device.Ble) {
        runOnUiThread {
            mDevices.add(ble)
            setNoDevicesVisible(mDevices.getVisible().isEmpty())
        }
    }

    @UiThread
    private fun setStatus(text :String?) {
        val l = findViewById<LinearLayout>(R.id.status_layout)
        if(text != null) {
            findViewById<TextView>(R.id.status_text).text = text
            l.visibility = View.VISIBLE
        } else {
            l.visibility = View.GONE
        }
    }

    @UiThread
    private fun setNoDevicesVisible(visible :Boolean) {
        findViewById<TextView>(R.id.no_devices_text)
                .visibility = if(visible) View.VISIBLE else View.GONE
    }

    @UiThread
    override fun onDeviceClicked(dev: Device) {
        stopScanning()
        if(dev.wifi != null) {
            startControllerActivity(dev.wifi!!)
            return
        }

        val devCfg = dev.ble!!.wifiCfg
        val deviceSsid = if(devCfg.stationMode) devCfg.name else dev.wifiApName

        val wifiMgr = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        if(!wifiMgr.isWifiEnabled) {
            showEnableWifiDialog()
            return
        }

        val ourSsid = if(wifiMgr.connectionInfo.networkId != -1) wifiMgr.connectionInfo.ssid.trim('"') else ""

        if(ourSsid == deviceSsid) {
            if(dev.ble!!.ip.hostAddress == "0.0.0.0") {
                showRescanDialog()
            } else {
                startControllerActivity(dev.ble!!)
            }
            return
        }

        mWifiConnector = WifiConnector
                .connect(this, deviceSsid, devCfg.password, object : WifiConnector.OnWifiConnectorDoneListener {
                    override fun onWifiConnectorDone(success: Boolean, errorStringId: Int?) {
                        onWifiConnectorDone(dev.ble!!, success, errorStringId)
                    }
                })
    }

    private fun showEnableWifiDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.enable_wifi)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
    }

    private fun showRescanDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.rescan_message)
                .setPositiveButton(R.string.rescan) { dialog, _ ->
                    dialog.dismiss()
                    startScan()
                }
                .setNegativeButton(android.R.string.cancel) { dialog,_ ->
                    dialog.dismiss()
                }
                .show()
    }

    @UiThread
    fun onWifiConnectorDone(device: Device.Ble, success: Boolean, errorStringId: Int?) {
        mWifiConnector = null
        if(success) {
            startControllerActivity(device)
        } else if(errorStringId != null) {
            Snackbar.make(findViewById<View>(R.id.device_list), errorStringId, Snackbar.LENGTH_LONG)
                    .show()
        }
    }

    @UiThread
    override fun onDeviceLongClicked(view: View, dev: Device): Boolean {
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return false

        if(dev.ble == null) {
            Snackbar.make(view, R.string.unable_to_configure_wifi, Snackbar.LENGTH_LONG).show()
            return true
        }

        val m = PopupMenu(this, view, Gravity.END)
        m.inflate(R.menu.item_discover)
        m.setOnMenuItemClickListener {
            when(it.itemId) {
                R.id.wifi_cfg -> {
                    val intent = Intent(this, WifiConfigList::class.java)
                    intent.putExtra("device", dev)
                    startActivityForResult(intent, ACT_WIFI_CONFIG)
                    true
                }
                else -> false
            }
        }
        m.show()
        return true
    }

    @UiThread
    private fun startControllerActivity(ble: Device.Ble) {
        startControllerActivity(Device.WiFi(ble.ip, ble.owner, ble.name))
    }

    @UiThread
    private fun startControllerActivity(wifi: Device.WiFi) {
        mLastDeviceName = wifi.name
        mLastDeviceHost = wifi.address.hostAddress

        val editor = getSharedPreferences("", MODE_PRIVATE).edit()
        editor.putString("lastDeviceName", mLastDeviceName)
        editor.putString("lastDeviceHost", mLastDeviceHost)
        editor.apply()

        val intent = Intent(this, ControllerActivity::class.java)
        intent.putExtra(WifiConfigList.EXTRA_DEVICE, wifi)
        startActivityForResult(intent, ACT_CONTROLLER)
    }

    private fun showRenameDialog() {
        val builder = AlertDialog.Builder(this)
        val layout = View.inflate(this, R.layout.dialog_edittext, null)
        val ed = layout.findViewById<EditText>(R.id.edittext)
        ed.setText(mDevices.getOwner())
        ed.setHint(R.string.owner_name)
        ed.filters = arrayOf(AsciiInputFilter())

        builder.setCancelable(true)
                .setView(layout)
                .setTitle(R.string.change_owner)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    mDevices.setOwner(ed.text.toString())

                    val e = getSharedPreferences("", Context.MODE_PRIVATE).edit()
                    e.putString("owner", mDevices.getOwner())
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

    private fun showPassDialog() {
        val builder = AlertDialog.Builder(this)
        val layout = View.inflate(this, R.layout.dialog_edittext, null)
        val ed = layout.findViewById<EditText>(R.id.edittext)
        ed.inputType = (InputType.TYPE_CLASS_TEXT.or(InputType.TYPE_TEXT_VARIATION_PASSWORD))
        ed.transformationMethod = PasswordTransformationMethod.getInstance()

        builder.setCancelable(true)
                .setView(layout)
                .setTitle(R.string.password)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    if(sha256(ed.text.toString()) == "5220a9d332af0fb806fb1a4ecd38f86dbacb78b5cf372a3155f7a8c3e466f811") {
                        setShowOtherPeoplesDevices(true)
                    } else {
                        Toast.makeText(this, "Invalid password.", Toast.LENGTH_SHORT).show()
                    }
                }
        val dialog = builder.create()
        dialog.show()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private fun sha256(input: String): String {
        val salt = "TK1tx1XUWZe+52M2eo2oebEZPhZ4gXmFzHQvl6v75VkFwwKuvqEUlcRFliPfSddeVuNwMtln1XHl:"
        val msdDigest = MessageDigest.getInstance("SHA-256")
        msdDigest.update(salt.toByteArray(charset("UTF-8")), 0, salt.length)
        msdDigest.update(input.toByteArray(charset("UTF-8")), 0, input.length)

        val result = StringBuffer()
        msdDigest.digest().forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }

        return result.toString()
    }
}
