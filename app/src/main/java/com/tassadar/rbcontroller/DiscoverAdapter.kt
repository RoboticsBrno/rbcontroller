package com.tassadar.rbcontroller

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class DiscoverAdapter(dataset: ArrayList<Device>, listener :OnDeviceClickedListener) : RecyclerView.Adapter<DiscoverAdapter.ViewHolder>() {
    class ViewHolder(val layout: ViewGroup) : RecyclerView.ViewHolder(layout)
    interface OnDeviceClickedListener {
        fun onDeviceClicked(dev :Device)
    }

    var owner :String = ""
    var showOtherPeoplesDevices = false

    private val mDataset = dataset
    private val mListener = listener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discover, parent, false) as ViewGroup
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mDataset.count { showOtherPeoplesDevices || it.owner == owner }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val l = holder.layout
        val dev = mDataset.get(position)

        if(dev.owner == owner) {
            l.alpha = 1.0f
            Log.e("test", "set to 1 $owner ${dev.owner} $position")
        } else {
            l.alpha = 0.35f
            Log.e("test", "set to 0.35 $owner ${dev.owner} $position")
        }

        l.findViewById<TextView>(R.id.name).text = dev.name
        l.findViewById<TextView>(R.id.address).text = dev.address.hostString
        l.findViewById<TextView>(R.id.desc).text = dev.desc

        val ownerText = l.findViewById<TextView>(R.id.owner)
        if(dev.owner == this.owner) {
            ownerText.visibility = View.GONE
        } else {
            ownerText.text = l.context.getString(R.string.owned_by, dev.owner)
            ownerText.visibility = View.VISIBLE
        }

        l.setOnClickListener(View.OnClickListener {
            mListener.onDeviceClicked(dev)
        })
    }

    fun addDevice(pos :Int, dev: Device) {
        mDataset.add(pos, dev)
        if(showOtherPeoplesDevices || dev.owner == owner)
            notifyItemInserted(pos)
    }
}