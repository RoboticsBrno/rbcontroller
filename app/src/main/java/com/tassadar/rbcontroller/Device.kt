package com.tassadar.rbcontroller

import android.os.Parcel
import android.os.Parcelable
import java.net.InetSocketAddress

data class Device(val address: InetSocketAddress,
                  val name: String, val desc: String,
                  val path: String, val port: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
            InetSocketAddress(parcel.readString(), parcel.readInt()),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readInt())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(address.hostString)
        parcel.writeInt(address.port)
        parcel.writeString(name)
        parcel.writeString(desc)
        parcel.writeString(path)
        parcel.writeInt(port)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Device> {
        override fun createFromParcel(parcel: Parcel): Device {
            return Device(parcel)
        }

        override fun newArray(size: Int): Array<Device?> {
            return arrayOfNulls(size)
        }
    }
}
