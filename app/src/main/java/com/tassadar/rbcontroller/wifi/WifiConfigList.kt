package com.tassadar.rbcontroller.wifi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.tassadar.rbcontroller.Device
import com.tassadar.rbcontroller.R
import com.tassadar.rbcontroller.ble.BleManager
import com.tassadar.rbcontroller.ble.WifiConfigWriteTask
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList


class WifiConfigList : AppCompatActivity(), WifiConfigListAdapter.OnWifiConfigClickListener, WifiConfigWriteTask.OnFinishedListener {
    companion object {
        private const val PREF_WIFI_CONFIGS = "wifiConfigs"
        private const val REQUEST_EDIT_CONFIG = 1

        const val EXTRA_DEVICE = "device"
        const val EXTRA_CONFIG_CURRENT = "config"
        const val EXTRA_CONFIG_ORIGINAL = "originalConfig"
        const val EXTRA_DELETE = "deleteConfig"
    }

    private val mLayoutManager = LinearLayoutManager(this)
    private val mConfigs = ArrayList<Device.WifiConfig>()
    private var mConfigsChanged = false

    private var mDeletedConfig: Device.WifiConfig? = null
    private var mDeletedSnackbar: Snackbar? = null

    private var mWriteTask: WifiConfigWriteTask? = null
    private var mWriteDialog: AlertDialog? = null
    private var mWritePreviousIdx: Int = -1

    private val mDevice: Device by lazy(LazyThreadSafetyMode.NONE) {
        intent.getParcelableExtra<Device>(EXTRA_DEVICE)!!
    }

    private val mAdapter: WifiConfigListAdapter by lazy(LazyThreadSafetyMode.NONE) {
        WifiConfigListAdapter(mConfigs, this, mDevice)
    }

    private val mConfigsComparator = Comparator<Device.WifiConfig> { a, b ->
        if(!a.stationMode && b.stationMode) {
            -1
        } else if(a.stationMode && !b.stationMode) {
            1
        } else if (a.stationMode) {
            if (a.channel != b.channel) {
                a.channel - b.channel
            } else {
                a.password.compareTo(b.password)
            }
        } else {
            val c = a.name.compareTo(b.name)
            if(c != 0) {
                c
            } else {
                a.password.compareTo(b.password)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_config_list)

        supportActionBar?.title = getString(R.string.wifi_config_title, mDevice.name)

        findViewById<RecyclerView>(R.id.configs_list).apply {
            itemAnimator = null
            setHasFixedSize(true)
            layoutManager = mLayoutManager
            adapter = mAdapter
        }

        findViewById<FloatingActionButton>(R.id.btn_add_config).setOnClickListener {
            startConfigEdit(null)
        }

        loadDevices()

        if(mDevice.ble == null) {
            finish()
        }

        addConfig(mDevice.ble!!.wifiCfg, true)
    }

    override fun onPause() {
        super.onPause()
        cancelDeviceWrite()
        saveConfigIfChanged()
    }

    @UiThread
    private fun saveConfigIfChanged() {
        if(mConfigsChanged) {
            val editor = getPreferences(Context.MODE_PRIVATE).edit()
            editor.putStringSet(PREF_WIFI_CONFIGS, mConfigs.map { it.format() }.toSet())
            editor.apply()
            mConfigsChanged = false
        }
    }

    @UiThread
    private fun loadDevices() {
        if(mConfigs.size != 0) {
            mAdapter.setSelected(-1)
            val prevSize = mConfigs.size
            mConfigs.clear()
            mAdapter.notifyItemRangeRemoved(0, prevSize)
        }

        val prefs = getPreferences(Context.MODE_PRIVATE)
        val configs = prefs.getStringSet(PREF_WIFI_CONFIGS, null)
        if(configs.isNullOrEmpty())
            return

        mConfigs.addAll(configs.mapNotNull {
            try {
                Device.WifiConfig.parse(it)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                null
            }
        })
        mConfigs.sortWith(mConfigsComparator)
        mAdapter.notifyItemRangeInserted(0, mConfigs.size)
        updateDescriptionVisibility()
    }

    @UiThread
    private fun addConfig(cfg: Device.WifiConfig, selected: Boolean = false) {
        mConfigs.indexOf(cfg).takeIf { it != -1 }?.let { idx ->
            if(selected) {
                mAdapter.setSelected(idx)
            }
            mLayoutManager.scrollToPosition(idx)
            return
        }

        val newPos = Collections
                .binarySearch(mConfigs, cfg, mConfigsComparator)
                .let { if(it < 0) -it-1 else it }

        mConfigs.add(newPos, cfg)
        mAdapter.onConfigAdded(newPos)
        mConfigsChanged = true

        if(selected) {
            mAdapter.setSelected(newPos)
        }
        mLayoutManager.scrollToPosition(newPos)

        updateDescriptionVisibility()
    }

    @UiThread
    private fun removeConfig(cfg: Device.WifiConfig) {
        mConfigs.indexOf(cfg)
                .takeIf { it != -1 }
                ?.let { removeConfig(it) }
    }

    @UiThread
    private fun removeConfig(idx :Int) {
        mDeletedConfig = mConfigs[idx]

        mConfigs.removeAt(idx)
        mAdapter.onConfigRemoved(idx)
        mConfigsChanged = true
        updateDescriptionVisibility()

        mDeletedSnackbar?.dismiss()

        val msg = getString(R.string.wifi_cfg_deleted, mDeletedConfig?.name)
        mDeletedSnackbar = Snackbar.make(findViewById(R.id.configs_list), msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    addConfig(mDeletedConfig!!, false)
                }
                .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(bar: Snackbar?, @DismissEvent event: Int) {
                        mDeletedSnackbar = null
                        mDeletedConfig = null
                    }
                })
        mDeletedSnackbar!!.show()
    }

    @UiThread
    private fun updateDescriptionVisibility() {
        val vis = if(mConfigs.size <= 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.no_configs_layout).visibility = vis
    }

    @UiThread
    private fun startConfigEdit(cfg: Device.WifiConfig?) {
        val intent = Intent(this, WifiConfigEdit::class.java)
        intent.putExtra(EXTRA_DEVICE, mDevice)
        if(cfg != null) {
            intent.putExtra(EXTRA_CONFIG_CURRENT, cfg)
        }
        startActivityForResult(intent, REQUEST_EDIT_CONFIG)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQUEST_EDIT_CONFIG -> {
                if(resultCode != Activity.RESULT_OK || data == null)
                    return

                val orig = data.getParcelableExtra<Device.WifiConfig>(EXTRA_CONFIG_ORIGINAL)

                if(data.getBooleanExtra(EXTRA_DELETE, false)) {
                    if(orig != null)
                        removeConfig(orig)
                    return
                }

                val newCfg = data.getParcelableExtra<Device.WifiConfig>(EXTRA_CONFIG_CURRENT) ?: return
                val sameAsNewIdx = mConfigs.indexOf(newCfg)
                when {
                    orig != null -> {
                        val origIdx = mConfigs.indexOf(orig)
                        if(origIdx == mAdapter.getSelected()) {
                            mAdapter.setSelected(-1)
                        }
                        if(sameAsNewIdx == -1) {
                            mConfigs[origIdx] = newCfg
                            mAdapter.notifyItemChanged(origIdx)
                            mLayoutManager.scrollToPosition(origIdx)
                        } else {
                            mConfigs.removeAt(origIdx)
                            mAdapter.onConfigRemoved(origIdx)
                            mLayoutManager.scrollToPosition(sameAsNewIdx)
                        }
                        mConfigsChanged = true
                    }
                    sameAsNewIdx != -1 -> {
                        mLayoutManager.scrollToPosition(sameAsNewIdx)
                    }
                    else -> {
                        addConfig(newCfg)
                    }
                }

                saveConfigIfChanged()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @UiThread
    override fun onDeviceClicked(cfg: Device.WifiConfig) {
        mWritePreviousIdx = mAdapter.getSelected()
        val idx = mConfigs.indexOf(cfg)
        mAdapter.setSelected(idx)
        writeToDevice(cfg)
    }

    override fun onDeviceLongClicked(view: View, cfg: Device.WifiConfig): Boolean {
        val p = PopupMenu(this, view, Gravity.END)
        p.inflate(R.menu.item_wifi_config)
        p.setOnMenuItemClickListener{
            when(it.itemId) {
                R.id.edit -> {
                    startConfigEdit(cfg)
                    true
                }
                R.id.delete -> {
                    removeConfig(cfg)
                    true
                }
                else -> false
            }
        }
        p.show()
        return true
    }

    @UiThread
    private fun writeToDevice(cfg: Device.WifiConfig) {
        val params = WifiConfigWriteTask.Params(
                this,
                mDevice.ble!!.mac,
                arrayOf(Pair(BleManager.RBCONTROL_SERVICE_UUID, BleManager.WIFI_CONFIG_UUID))
        ) { _, char ->
            char.setValue(cfg.format())
        }

        mWriteTask = WifiConfigWriteTask(this)
        mWriteTask!!.execute(params)

        val layout = View.inflate(this, R.layout.dialog_progress, null)
        layout.findViewById<TextView>(R.id.text).setText(R.string.writing_wifi_config)

        mWriteDialog = AlertDialog.Builder(this)
                .setView(layout)
                .setCancelable(true)
                .setOnDismissListener { cancelDeviceWrite() }
                .show()
    }

    @UiThread
    private fun cancelDeviceWrite() {
        if(mWriteTask == null)
            return

        if(mWritePreviousIdx != -1)
            mAdapter.setSelected(mWritePreviousIdx)

        mWriteTask!!.realCancel()
        mWriteTask = null

        mWriteDialog!!.dismiss()
        mWriteDialog = null
    }

    @UiThread
    override fun onWriteWifiConfigDone(errorString: Int?) {
        if(errorString != null) {
            cancelDeviceWrite()

            val msg = getString(R.string.wifi_write_error).format(getString(errorString))
            Snackbar.make(findViewById<View>(R.id.configs_list), msg, Snackbar.LENGTH_LONG).show()
        } else {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
