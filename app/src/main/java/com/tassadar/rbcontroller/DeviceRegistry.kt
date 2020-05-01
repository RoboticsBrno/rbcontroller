package com.tassadar.rbcontroller

import android.os.Bundle
import androidx.annotation.UiThread
import java.util.*

class DeviceRegistry(listener: DiscoverAdapter.OnDeviceClickedListener, bleSupported: Boolean) {
    private val mDevices = ArrayList<Device>()
    private val mVisibleDevices = ArrayList<Device>()
    private var mShowOtherPeoplesDevices = false
    private var mOwner = ""
    private val mAdapter = DiscoverAdapter(mVisibleDevices, listener, bleSupported)
    private val mDevicesByMac = HashMap<String, Device>()

    private val mVisibleFilter: (Device) -> Boolean = { dev ->
        mShowOtherPeoplesDevices || dev.owner == mOwner
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable("devices", mDevices)
    }

    fun loadSavedInstance(savedInstanceState: Bundle) {
        try {
            @Suppress("UNCHECKED_CAST")
            val savedDevices = savedInstanceState.getSerializable("devices") as ArrayList<Device>
            if (savedDevices.isNullOrEmpty()) {
                return
            }
            mDevices.addAll(savedDevices)

            val prevSize = mVisibleDevices.size
            val filtered = savedDevices.filter(mVisibleFilter)
            mVisibleDevices.addAll(filtered)
            mAdapter.notifyItemRangeInserted(prevSize, filtered.size)
        } catch(e :ClassCastException) {
            e.printStackTrace()
        }
    }

    @UiThread
    fun getAdapter(): DiscoverAdapter {
        return mAdapter
    }

    @UiThread
    fun getVisible(): List<Device> {
        return mVisibleDevices
    }

    @UiThread
    fun getAll(): List<Device> {
        return mDevices
    }

    @UiThread
    fun setShowOtherPeoplesDevices(show: Boolean) {
        mShowOtherPeoplesDevices = show
        refilterDevices()
    }

    @UiThread
    fun getShowOtherPeoplesDevices(): Boolean {
        return mShowOtherPeoplesDevices
    }

    @UiThread
    fun setOwner(owner: String) {
        mOwner = owner
        mAdapter.owner = owner
        refilterDevices()
    }

    @UiThread
    fun getOwner(): String {
        return mOwner
    }

    @UiThread
    fun clear() {
        mDevices.clear()
        mDevicesByMac.clear()
        val size = mVisibleDevices.size
        if(size != 0) {
            mVisibleDevices.clear()
            mAdapter.notifyItemRangeRemoved(0, size)
        }
    }

    @UiThread
    fun clearBle() {
        val itr = mDevices.iterator()
        while(itr.hasNext()) {
            val dev = itr.next()
            dev.ble = null
            if(dev.wifi == null) {
                itr.remove()
            }
        }
        refilterDevices()
    }

    private fun findDevice(dev: Device): Device? {
        return mDevices.find { i ->
            dev.owner == i.owner && dev.name == i.name && dev.ip == i.ip &&
                    (dev.ble == null || i.ble == null || dev.ble!!.mac == i.ble!!.mac)
        }
    }

    @UiThread
    fun add(wifi: Device.WiFi) {
        val dev = findDevice(Device(wifi=wifi))
        if(dev != null) {
            dev.wifi = wifi
            notifyChangedIfVisible(dev)
        } else {
            add(Device(wifi))
        }
    }

    @UiThread
    fun add(ble: Device.Ble) {
        var dev = mDevicesByMac[ble.mac]
        if(dev?.ip == ble.ip) {
            dev.ble = ble
            notifyChangedIfVisible(dev)
            return
        }

        if(dev != null) {
            val visible = mVisibleFilter(dev)
            dev.ble = null
            if(dev.wifi == null) {
                mDevices.remove(dev)
                if(visible) {
                    val idx = mVisibleDevices.indexOf(dev)
                    mVisibleDevices.removeAt(idx)
                    mAdapter.notifyItemRemoved(idx)
                }
            } else {
                notifyChangedIfVisible(dev)
            }
        }

        dev = findDevice(Device(ble = ble))
        if(dev != null) {
            dev.ble = ble
            notifyChangedIfVisible(dev)
        } else {
            dev = Device(ble=ble)
            add(dev)
        }
        mDevicesByMac[ble.mac] = dev
    }

    @UiThread
    private fun add(dev: Device) {
        mDevices.add(findPosForDevice(mDevices, dev), dev)
        if(mVisibleFilter(dev)) {
            val pos = findPosForDevice(mVisibleDevices, dev)
            mVisibleDevices.add(pos, dev)
            mAdapter.notifyItemInserted(pos)
        }
    }

    @UiThread
    private fun refilterDevices() {
        mVisibleDevices.clear()
        mVisibleDevices.addAll(mDevices.filter(mVisibleFilter))
        mAdapter.notifyDataSetChanged()
    }

    @UiThread
    private fun notifyChangedIfVisible(dev :Device) {
        val idx = mVisibleDevices.indexOf(dev)
        if(idx != -1) {
            mAdapter.notifyItemChanged(idx)
        }
    }

    private fun findPosForDevice(devices: List<Device>, dev: Device): Int {
        val pos = Collections.binarySearch(devices, dev) { a, b ->
            val aIsOur = a.owner == mOwner
            val bIsOur = b.owner == mOwner
            if(aIsOur && !bIsOur) {
                -1
            } else if (!aIsOur && !bIsOur) {
                1
            } else {
                a.name.compareTo(b.name)
            }
        }

        return if(pos < 0) -pos-1 else pos
    }
}
