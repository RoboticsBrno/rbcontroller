package com.tassadar.rbcontroller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DiscoverAdapter(
        private val mDataset: List<Device>,
        private val mListener :OnDeviceClickedListener,
        private val mBleSupported: Boolean) : RecyclerView.Adapter<DiscoverAdapter.ViewHolder>() {
    class ViewHolder(val layout: ViewGroup) : RecyclerView.ViewHolder(layout)
    interface OnDeviceClickedListener {
        fun onDeviceClicked(dev :Device)
        fun onDeviceLongClicked(view: View, dev: Device): Boolean
    }

    var owner: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discover, parent, false) as ViewGroup
        if(!mBleSupported) {
            view.findViewById<View>(R.id.ic_bluetooth).visibility = View.GONE
        }
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val l = holder.layout
        val dev = mDataset[position]

        l.alpha = if(dev.owner == owner) 1.0f else 0.35f
        l.findViewById<TextView>(R.id.name).text = dev.name
        l.findViewById<TextView>(R.id.address).text = dev.wifi?.address?.hostAddress ?: dev.ble?.mac ?: ""
        l.findViewById<TextView>(R.id.desc).text = dev.wifi?.desc ?: ""

        setIconState(l, R.id.ic_wifi, dev.wifi != null)
        if(mBleSupported)
            setIconState(l, R.id.ic_bluetooth, dev.ble != null)

        l.findViewById<ImageView>(R.id.ic_battery).visibility = if(dev.ble != null) View.VISIBLE else View.GONE
        l.findViewById<TextView>(R.id.battery_pct).apply {
            visibility = if (dev.ble != null) View.VISIBLE else View.GONE
            text = "${dev.ble?.batteryPct}%"
        }

        l.findViewById<ImageView>(R.id.ic_wifi_ap).visibility = if(dev.ble?.wifiCfg?.stationMode == false) View.VISIBLE else View.INVISIBLE

        val ownerText = l.findViewById<TextView>(R.id.owner)
        if(dev.owner == this.owner) {
            ownerText.visibility = View.GONE
        } else {
            ownerText.text = l.context.getString(R.string.owned_by, dev.owner)
            ownerText.visibility = View.VISIBLE
        }

        l.setOnClickListener {
            mListener.onDeviceClicked(dev)
        }
        l.setOnLongClickListener {
            mListener.onDeviceLongClicked(it, dev)
        }
    }

    private fun setIconState(l: ViewGroup, id :Int, on: Boolean) {
        val ic = l.findViewById<ImageView>(id)
        ic.alpha = if(on) 1.0f else 0.15f
        ic.setColorFilter(if(on) l.resources.getColor(R.color.colorPrimary) else 0)
    }
}
