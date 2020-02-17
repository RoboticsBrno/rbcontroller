package com.tassadar.rbcontroller.ble

import android.bluetooth.*
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.GuardedBy
import com.tassadar.rbcontroller.R
import java.lang.ref.WeakReference
import java.util.*


class WifiConfigWriteTask(listener: OnFinishedListener) : AsyncTask<Any, Void, Int?>() {
    interface OnFinishedListener {
        fun onWriteWifiConfigDone(errorString: Int?)
    }

    data class Params(
            val context: Context,
            val mac: String,
            val charUuids: Array<Pair<UUID, UUID>>,
            val charValueCb: (idx: Int, char: BluetoothGattCharacteristic) -> Unit)

    companion object {
        private const val LOG = "RB:WifiConfigWrite"

        private const val STATE_CONNECTING = 0
        private const val STATE_FAILED = 1
        private const val STATE_WRITE_READY = 3
        private const val STATE_WRITING = 4

        private const val MAX_RETRIES = 2
    }

    private val mListener = WeakReference(listener)

    private val mLock = Object()

    @GuardedBy("mLock")
    private var mState = STATE_CONNECTING
    @GuardedBy("mLock")
    private var mWriteCharIdx = 0

    override fun doInBackground(vararg paramsVar: Any): Int? {
        val params = paramsVar[0] as Params

        val manager = params.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if(adapter == null || !adapter.isEnabled) {
            return R.string.bluetooth_not_enabled
        }

        val btDev = try {
            adapter.getRemoteDevice(params.mac)
        } catch(e :IllegalArgumentException) {
            return R.string.bluetooth_invalid_device
        }

        for(i in 1..MAX_RETRIES) {
            switchState(STATE_CONNECTING)

            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                btDev.connectGatt(params.context, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                btDev.connectGatt(params.context, false, mGattCallback)
            }

            try {
                when (val res = gattStateMachine(gatt, params)) {
                    R.string.bluetooth_connect_failed -> {}
                    else -> return res
                }
            } finally {
                gatt.close()
            }
        }
        return R.string.bluetooth_connect_failed
    }

    fun realCancel() {
        if(cancel(false)) {
            synchronized(mLock) {
                mLock.notify()
            }
        }
    }

    override fun onPostExecute(result: Int?) {
        mListener.get()?.onWriteWifiConfigDone(result)
    }

    private fun gattStateMachine(gatt: BluetoothGatt, params: Params): Int? = synchronized<Int?>(mLock) {
        while (true) {
            mLock.wait()
            if (isCancelled) {
                return null
            }

            when (mState) {
                STATE_FAILED -> {
                    return R.string.bluetooth_connect_failed
                }
                STATE_WRITE_READY -> {
                    val r = writeNextCharacteristic(gatt, params)
                    if(r == null || r != 0)
                        return r
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return 0 // for the type checks...
    }

    private fun switchState(newState: Int) = synchronized(mLock) {
        if(newState != mState) {
            mState = newState
            if(mState == STATE_CONNECTING)
                mWriteCharIdx = 0
            mLock.notify()
        }
    }

    @GuardedBy("mLock")
    private fun writeNextCharacteristic(gatt: BluetoothGatt, params: Params): Int? {
        if(mWriteCharIdx >= params.charUuids.size)
            return null

        val pair = params.charUuids[mWriteCharIdx]
        val service = gatt.getService(pair.first) ?: return R.string.bluetooth_no_service
        val char = service.getCharacteristic(pair.second) ?: return R.string.bluetooth_no_characteristic

        params.charValueCb(mWriteCharIdx, char)

        switchState(STATE_WRITING)

        gatt.writeCharacteristic(char)
        mWriteCharIdx++
        return 0
    }

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(LOG, "onConnectionStateChange $status $newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val services = gatt.services
                if (services.isNullOrEmpty()) {
                    gatt.discoverServices()
                } else {
                    onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
                }
            } else {
                synchronized(mLock) {
                    when(mState) {
                        STATE_CONNECTING, STATE_WRITING -> switchState(STATE_FAILED)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> switchState(STATE_WRITE_READY)
                else -> gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, chr: BluetoothGattCharacteristic, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> switchState(STATE_WRITE_READY)
                else -> gatt.disconnect()
            }
        }
    }
}
