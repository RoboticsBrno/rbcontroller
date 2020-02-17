package com.tassadar.rbcontroller

import android.os.Parcel
import android.os.Parcelable
import java.net.InetAddress

data class Device(var wifi: WiFi? = null, var ble: Ble? = null): Parcelable {
    val owner: String
        get() = if(wifi != null) wifi!!.owner else ble!!.owner
    val name: String
        get() = if(wifi != null) wifi!!.name else ble!!.name
    val ip: InetAddress
        get() = if(wifi != null) wifi!!.address else ble!!.ip
    val wifiApName: String
        get() = "${owner}-${name}".take(31)

    constructor(parcel: Parcel) : this(
            parcel.readParcelable(Device::class.java.classLoader),
            parcel.readParcelable(Device::class.java.classLoader))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(wifi, 0)
        parcel.writeParcelable(ble, 0)
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

    data class WiFi(val address: InetAddress,
                    val owner: String,
                    val name: String,
                    val desc: String = "",
                    val path: String = "/index.html",
                    val port: Int = 80) : Parcelable {
        constructor(parcel: Parcel) : this(
                InetAddress.getByName(parcel.readString()),
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(address.hostAddress)
            parcel.writeString(owner)
            parcel.writeString(name)
            parcel.writeString(desc)
            parcel.writeString(path)
            parcel.writeInt(port)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<WiFi> {
            override fun createFromParcel(parcel: Parcel): WiFi {
                return WiFi(parcel)
            }

            override fun newArray(size: Int): Array<WiFi?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class Ble(
            val mac: String,
            val ip: InetAddress,
            val owner: String,
            val name: String,
            val batteryPct: Int,
            val wifiCfg: WifiConfig) : Parcelable {

        constructor(parcel: Parcel) : this(
                parcel.readString()!!,
                InetAddress.getByName(parcel.readString()!!),
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readParcelable(Ble::class.java.classLoader)!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(mac)
            parcel.writeString(ip.hostAddress)
            parcel.writeString(owner)
            parcel.writeString(name)
            parcel.writeInt(batteryPct)
            parcel.writeParcelable(wifiCfg, 0)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Ble> {
            override fun createFromParcel(parcel: Parcel): Ble {
                return Ble(parcel)
            }

            override fun newArray(size: Int): Array<Ble?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class WifiConfig(val stationMode: Boolean,
                          val name: String,
                          val password: String,
                          val channel: Int) : Parcelable {

        constructor(parcel: Parcel) : this(
                (parcel.readInt() == 1),
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(if(stationMode) 1 else 0)
            parcel.writeString(name)
            parcel.writeString(password)
            parcel.writeInt(channel)
        }

        override fun describeContents(): Int {
            return 0
        }

        fun format(): String {
            return ("${if(stationMode) "1" else "0"}$SEP" +
                    "$name$SEP" +
                    "$password$SEP" +
                    "$channel$SEP")
        }

        override fun equals(other: Any?): Boolean {
            if(other == null || other !is WifiConfig || stationMode != other.stationMode)
                return false

            return if(stationMode) {
                name == other.name && password == other.password
            } else {
                password == other.password && channel == other.channel
            }
        }

        override fun hashCode(): Int {
            var result = stationMode.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + password.hashCode()
            result = 31 * result + channel
            return result
        }

        companion object CREATOR : Parcelable.Creator<WifiConfig> {
            private const val SEP = '\n'

            @Throws(java.lang.IllegalArgumentException::class)
            fun parse(data: String): WifiConfig {
                val parts = data.split(SEP)
                if(parts.size < 4)
                    throw IllegalArgumentException("wrong number of parts (${parts.size})")
                return WifiConfig(
                        (parts[0] == "1"),
                        parts[1],
                        parts[2],
                        parts[3].toInt())
            }

            override fun createFromParcel(parcel: Parcel): WifiConfig {
                return WifiConfig(parcel)
            }

            override fun newArray(size: Int): Array<WifiConfig?> {
                return arrayOfNulls(size)
            }
        }
    }
}
