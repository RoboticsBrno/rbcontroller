package com.tassadar.rbcontroller.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.tassadar.rbcontroller.Device
import java.net.InetAddress
import java.util.*

internal class ScannedDevice(private val gatt: BluetoothGatt) {
    companion object {
        const val LOG = "RB:BleDevice"
        
        private val toReadCharacteristics = hashSetOf(
                BleManager.OWNER_UUID,
                BleManager.NAME_UUID,
                BleManager.WIFI_IP_UUID,
                BleManager.WIFI_CONFIG_UUID,

                BleManager.BATTERY_LEVEL_UUID
        )
    }

    private enum class OpType {
        OP_READ_CHAR, OP_WRITE_CHAR, OP_CLOSE,
    }

    private data class OpQueueItem(
            val typ: OpType,
            val chr: BluetoothGattCharacteristic?)

    private val mOpThread = Thread { gattQueueProcessor() }

    private val mLock = Object()
    private val mOpQueue = LinkedList<OpQueueItem>()
    private var mOpInFlight = false
    private var mClosed = false

    private val mRemainingReads = HashSet(toReadCharacteristics)

    init {
        mOpThread.start()
    }

    fun close() {
        synchronized(mLock) {
            if(mClosed)
                return
            mClosed = true;

            mOpQueue.clear()
            mOpQueue.add(OpQueueItem(OpType.OP_CLOSE, null))
            onOpDone()
        }
        mOpThread.join()
        gatt.close()
    }

    fun onServicesDiscovered() {
        gatt.services
                .flatMap { it.characteristics }
                .filter { mRemainingReads.contains(it.uuid) }
                .forEach { queueReadCharacteristic(it) }
    }

    fun onCharacteristicRead(chr: BluetoothGattCharacteristic): Device.Ble? {
        onOpDone()
        debugCharValue(chr)

        val readsDone = synchronized(mRemainingReads) {
            mRemainingReads.remove(chr.uuid)
            mRemainingReads.size == 0
        }

        if(readsDone) {
            gatt.disconnect()
            try {
                return buildDevice()
            } catch(e :java.lang.IllegalArgumentException) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun debugCharValue(chr: BluetoothGattCharacteristic) {
        when(chr.uuid) {
            BleManager.WIFI_IP_UUID -> {
                val addr = InetAddress.getByAddress(chr.value)
                Log.d(LOG, "read char ${chr.uuid} ${addr.hostAddress}")
            }
            BleManager.BATTERY_LEVEL_UUID -> {
                val pct = chr.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                Log.d(LOG, "read char ${chr.uuid} ${pct}%")
            }
            else -> {
                Log.d(LOG, "read char ${chr.uuid} ${String(chr.value)}")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun buildDevice(): Device.Ble {
        val rb = gatt.getService(BleManager.RBCONTROL_SERVICE_UUID)
        val batt = gatt.getService(BleManager.BATTERY_SERVICE_UUID)

        return Device.Ble(
                gatt.device.address,
                InetAddress.getByAddress(rb.getCharacteristic(BleManager.WIFI_IP_UUID).value),
                rb.getCharacteristic(BleManager.OWNER_UUID).getStringValue(0),
                rb.getCharacteristic(BleManager.NAME_UUID).getStringValue(0),
                batt.getCharacteristic(BleManager.BATTERY_LEVEL_UUID).getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0),
                Device.WifiConfig.parse(rb.getCharacteristic(BleManager.WIFI_CONFIG_UUID).getStringValue(0)))
    }

    private fun queueReadCharacteristic(chr: BluetoothGattCharacteristic) = synchronized(mLock) {
        mOpQueue.addLast(OpQueueItem(OpType.OP_READ_CHAR, chr))
        mLock.notify()
    }

    private fun onOpDone() = synchronized(mLock) {
        mOpInFlight = false
        mLock.notify()
    }

    private fun gattQueueProcessor() = synchronized(mLock) {
        while(true) {
            mLock.wait()

            if (mOpInFlight || mOpQueue.isEmpty()) {
                continue
            }

            val it = mOpQueue.pop()
            when (it.typ) {
                OpType.OP_CLOSE -> {
                    return
                }
                OpType.OP_READ_CHAR -> {
                    mOpInFlight = true
                    gatt.readCharacteristic(it.chr!!)
                }
                OpType.OP_WRITE_CHAR -> {
                    mOpInFlight = true
                    gatt.writeCharacteristic(it.chr!!)
                }
            }
        }
    }
}
