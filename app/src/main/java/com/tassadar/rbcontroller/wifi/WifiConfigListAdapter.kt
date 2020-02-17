package com.tassadar.rbcontroller.wifi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.tassadar.rbcontroller.Device
import com.tassadar.rbcontroller.R


class WifiConfigListAdapter(
        private val mDataset: List<Device.WifiConfig>,
        private val mListener : OnWifiConfigClickListener,
        private val mDevice: Device)
    : RecyclerView.Adapter<WifiConfigListAdapter.ViewHolder>() {

    class ViewHolder(val layout: ViewGroup) : RecyclerView.ViewHolder(layout)

    interface OnWifiConfigClickListener {
        fun onDeviceClicked(cfg : Device.WifiConfig)
        fun onDeviceLongClicked(view: View, cfg: Device.WifiConfig): Boolean
    }

    private var mSelectedIdx: Int = -1

    @UiThread
    fun setSelected(idx: Int) {
        if(mSelectedIdx != -1) {
            notifyItemChanged(mSelectedIdx)
        }
        mSelectedIdx = if(idx >= -1 && idx < mDataset.size) idx else -1
        if(mSelectedIdx != -1) {
            notifyItemChanged(mSelectedIdx)
        }
    }

    @UiThread
    fun getSelected(): Int {
        return mSelectedIdx
    }

    fun onConfigAdded(pos: Int) {
        if(pos <= mSelectedIdx) {
            ++mSelectedIdx
        }
        notifyItemInserted(pos)
    }

    fun onConfigRemoved(pos: Int) {
        if(pos <= mSelectedIdx) {
            --mSelectedIdx
        }
        notifyItemRemoved(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wifi_config, parent, false) as ViewGroup
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val l = holder.layout
        val cfg = mDataset[position]

        l.findViewById<ImageView>(R.id.ic_mode).apply {
            setImageResource(if (cfg.stationMode) R.drawable.ic_wifi else R.drawable.ic_wifi_ap)
            setColorFilter(if (cfg.stationMode)
                resources.getColor(R.color.colorPrimary) else
                resources.getColor(R.color.colorAccent))
        }

        if(cfg.stationMode) {
            l.findViewById<TextView>(R.id.mode).setText(R.string.mode_station)
        } else {
            l.findViewById<TextView>(R.id.mode).text = l.resources.getString(R.string.mode_ap, cfg.channel)
        }

        l.findViewById<TextView>(R.id.name).text = if(cfg.stationMode) cfg.name else mDevice.wifiApName
        l.findViewById<TextView>(R.id.password).text = formatPassword(cfg.password)
        l.findViewById<RadioButton>(R.id.current).isChecked = position == mSelectedIdx

        l.setOnClickListener {
            mListener.onDeviceClicked(cfg)
        }
        l.setOnLongClickListener {
            mListener.onDeviceLongClicked(it, cfg)
        }
    }

    private fun formatPassword(pass: String): String {
        val half = pass.length/2
        return pass.take(half) + "*".repeat(pass.length-half)
    }
}
