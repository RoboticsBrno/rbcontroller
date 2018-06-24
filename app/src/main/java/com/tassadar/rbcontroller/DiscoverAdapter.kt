package com.tassadar.rbcontroller

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class DiscoverAdapter(dataset: ArrayList<Device>, listener :OnDeviceClickedListener) : RecyclerView.Adapter<DiscoverAdapter.ViewHolder>() {
    class ViewHolder(val layout: ViewGroup) : RecyclerView.ViewHolder(layout)
    interface OnDeviceClickedListener {
        fun onDeviceClicked(dev :Device)
    }

    private val mDataset = dataset
    private val mListener = listener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discover, parent, false) as ViewGroup
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val l = holder.layout
        val dev = mDataset.get(position)

        l.findViewById<TextView>(R.id.name).text = dev.name
        l.findViewById<TextView>(R.id.address).text = dev.address.hostString
        l.findViewById<TextView>(R.id.desc).text = dev.desc

        l.setOnClickListener(View.OnClickListener {
            mListener.onDeviceClicked(dev)
        })
    }
}