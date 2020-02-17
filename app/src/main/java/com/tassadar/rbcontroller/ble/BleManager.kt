package com.tassadar.rbcontroller.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.tassadar.rbcontroller.DiscoverActivity
import com.tassadar.rbcontroller.R
import java.util.*

class BleManager : BluetoothAdapter.LeScanCallback {
    companion object {
        val LOG = "RB::BleManager"

        val RBCONTROL_SERVICE_UUID = UUID.fromString("be669d73-de9c-409e-848e-889a5fc66c0e")
        val OWNER_UUID = UUID.fromString("c89b7fef-341f-43c9-a561-58e5476add31")
        val NAME_UUID = UUID.fromString("41b706e4-af8f-4544-a271-d484863526fd")
        val WIFI_IP_UUID = UUID.fromString("9def56ce-80d7-4c17-a68c-3cc480763bd4")
        val WIFI_CONFIG_UUID = UUID.fromString("f33ff70e-4b87-484d-bf95-ff856582c82c")

        val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    private data class DiscoveryItem(
            val dev: BluetoothDevice,
            var scanned: ScannedDevice? = null,
            var readCounter: Int = 0,
            var nextRead: Long = 0)

    private val MAX_READS_IN_PROGRESS = 3

    private val mDiscovered = HashMap<String, DiscoveryItem>()
    private var mReadsInProgress = 0
    private var mScanContext: DiscoverActivity? = null
    private var mBtAdapter: BluetoothAdapter? = null

    @UiThread
    fun init(act: Activity) {
        if(!act.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return

        if(ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return

        val bluetoothManager = act.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBtAdapter = bluetoothManager.adapter
        if(mBtAdapter != null && !mBtAdapter!!.isEnabled) {
            Snackbar.make(act.findViewById(android.R.id.content), R.string.enable_bt, Snackbar.LENGTH_SHORT).show()
        }
    }

    @UiThread
    fun onStop() {
        stopScanning()
        synchronized(mDiscovered) {
            for ((_, it) in mDiscovered) {
                it.scanned?.close()
            }
            mDiscovered.clear()
        }
    }

    @UiThread
    fun startScan(ctx: DiscoverActivity) {
        synchronized(mDiscovered) {
            for((_, it) in mDiscovered) {
                it.scanned?.close()
            }
            mDiscovered.clear()
            mReadsInProgress = 0

            if(mBtAdapter != null && mBtAdapter!!.isEnabled) {
                if(mBtAdapter!!.isDiscovering)
                    mBtAdapter!!.cancelDiscovery()
                mBtAdapter!!.startLeScan(arrayOf(RBCONTROL_SERVICE_UUID), this)
            }
            mScanContext = ctx
        }
    }

    @UiThread
    fun stopScanning() {
        if(mBtAdapter != null && mBtAdapter!!.isEnabled) {
            mBtAdapter!!.stopLeScan(this)
        }
        synchronized(mDiscovered) {
            mScanContext = null
        }
    }

    override fun onLeScan(dev: BluetoothDevice?, rssi: Int, scanData: ByteArray?) {
        synchronized(mDiscovered) {
            var it = mDiscovered[dev!!.address]
            if(it?.dev != null)
                return
            if(it == null) {
                it = DiscoveryItem(dev)
                mDiscovered[dev.address] = it
            }
            Log.d(DiscoverActivity.LOG, "Found BLE  ${dev.address} ${dev.name}")
            readNextDevice()
        }
    }

    private fun readNextDevice(): Unit = synchronized(mDiscovered) {
        if(mReadsInProgress >= MAX_READS_IN_PROGRESS || mScanContext == null)
            return

        val now = System.currentTimeMillis()
        mDiscovered.values
                .filter { it.scanned == null && now > it.nextRead }
                .minBy { it.readCounter }?.let { it ->
                    ++it.readCounter
                    ++mReadsInProgress
                    it.scanned = ScannedDevice(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        it.dev.connectGatt(mScanContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        it.dev.connectGatt(mScanContext, false, mGattCallback)
                    })
                }
    }

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int,  newState: Int) {
            if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val services = gatt.services
                if(services.isNullOrEmpty()) {
                    gatt.discoverServices()
                } else {
                    onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
                }
            } else {
                disconnectDevice(gatt, status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> synchronized(mDiscovered) {
                    mDiscovered[gatt.device.address]?.scanned?.onServicesDiscovered()
                }
                else -> disconnectDevice(gatt, status)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, chr: BluetoothGattCharacteristic, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> synchronized(mDiscovered) {
                    mDiscovered[gatt.device.address]?.scanned?.onCharacteristicRead(chr)
                            ?.let { mScanContext?.onBleDeviceDiscovered(it) }
                }
                else -> disconnectDevice(gatt, status)
            }
        }
    }

    private fun disconnectDevice(gatt: BluetoothGatt, status: Int) = synchronized(mDiscovered) {
        mDiscovered[gatt.device.address]?.let { dev ->
            dev.scanned?.close()
            dev.scanned = null
            if(status == BluetoothGatt.GATT_SUCCESS) {
                dev.nextRead = System.currentTimeMillis() + 1000 + (Math.random() * 1000).toLong()
            }
            --mReadsInProgress
        }
        readNextDevice()
    }

}
