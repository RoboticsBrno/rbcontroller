package com.tassadar.rbcontroller.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.tassadar.rbcontroller.R
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.Timer
import kotlin.concurrent.timerTask


object WifiConnector {
    interface OnWifiConnectorDoneListener {
        fun onWifiConnectorDone(success: Boolean, errorStringId: Int?)
    }

    private const val LOG = "RB:WifiConnector"

    private var mNetworkListener: AboveQNetworkListener? = null
    private var mNetworkId = -1

    // Following comment is left here for historical reference on Android team incompetence.
    // The AboveQ variant was re-enabled a year later, because Play Store now requires 29 as minimal
    // targetSdk. The UX is still shit, the bug on OnePlus is still not fixed.
    //
    // The ConnectivityManager is completely fucking broken on Android 10.
    //  * Connect to the requested network. Disconnects immediately because "App released request,
    //    cancelling NetworkRequest". The Request was, in fact, not fucking canceled, so it starts
    //    searching for the same bloody network again.
    //  * The UX is fucking horrible. You know the WiFi SSID and password. There's nothing to be figured out.
    //    The os still takes like 30s to "search" for the network, and then needs user confirmation
    //    as to which (there's only ONE!) it should connect to.
    //
    // As you can tell, many hours were wasted on this trash.
    fun connect(ctx: Context, ssid: String, password: String, listener: OnWifiConnectorDoneListener): Closeable {
        val connector = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mNetworkListener?.close(ctx)
            mNetworkListener = null
            AboveQ(ctx, listener, ssid, password)
        } else {
            BelowQ(ctx, listener, ssid, password)
        }
        connector.connect()
        return connector
    }

    fun releaseNetwork(ctx: Context) = synchronized(this) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mNetworkListener?.close(ctx)
            mNetworkListener = null
        }
        if(mNetworkId != -1) {
            val mgr = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            mgr.disableNetwork(mNetworkId)
            mNetworkId = -1
        }
    }

    private abstract class WifiConnectorBase(
            protected val mContext: Context,
            private var mListener: OnWifiConnectorDoneListener?,
            protected val mSsid: String,
            protected val mPassword: String) : Closeable{
        private var mDialog: AlertDialog? = null

        open fun connect() {
            val layout = View.inflate(mContext, R.layout.dialog_progress, null)
            layout.findViewById<TextView>(R.id.text).text = mContext.getString(R.string.connecting_to_wifi, mSsid)

            mDialog = AlertDialog.Builder(mContext)
                    .setView(layout)
                    .setCancelable(true)
                    .setOnDismissListener {
                        returnAndClose(false, null)
                    }
                    .show()
        }

        fun returnAndClose(success: Boolean, errorStringId: Int?) {
            close()
            callDoneListener(success, errorStringId)
        }

        private fun callDoneListener(success: Boolean, errorStringId: Int?) = synchronized(this) {
            mListener?.onWifiConnectorDone(success, errorStringId)
            mListener = null
        }

        override fun close() {
            mDialog?.dismiss()
            mDialog = null
        }
    }

    @Suppress("DEPRECATION")
    private class BelowQ(
            mContext: Context,
            mListener: OnWifiConnectorDoneListener,
            mSsid: String,
            mPassword: String) : WifiConnectorBase(mContext, mListener, mSsid, mPassword) {

        private val mMgr = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        private val mTimer = Timer()

        override fun close() {
            super.close()
            mTimer.cancel()
            try {
                mContext.unregisterReceiver(mReceiver)
            } catch(e :IllegalArgumentException) {}
        }

        @SuppressLint("MissingPermission")
        override fun connect() {
            super.connect()

            val cfg = WifiConfiguration().apply {
                SSID = "\"$mSsid\""
                if(mPassword.isNotEmpty()) {
                    preSharedKey = "\"$mPassword\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                status = WifiConfiguration.Status.ENABLED
            }

            val existingCfg = mMgr.configuredNetworks.find { it.SSID == cfg.SSID }

            val networkId = if(existingCfg != null) {
                cfg.networkId = existingCfg.networkId
                val newId = mMgr.updateNetwork(cfg) // best-effort, might have been created by another app
                if(newId != -1) newId else existingCfg.networkId
            } else {
                mMgr.addNetwork(cfg)
            }

            if(networkId == -1) {
                returnAndClose(false, R.string.wifi_failed_to_add)
                return
            }

            val currentNetwork = mMgr.connectionInfo
            if(currentNetwork != null && currentNetwork.networkId != -1) {
                mMgr.disableNetwork(currentNetwork.networkId)
            }

            val filter = IntentFilter()
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            ContextCompat.registerReceiver(mContext, mReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            if (!mMgr.enableNetwork(networkId, true)) {
                returnAndClose(false, R.string.wifi_failed_to_enable)
                return
            }

            mTimer.schedule(timerTask {
                returnAndClose(false, R.string.wifi_timed_out)
            }, 30000)
        }

        private val mReceiver = object : BroadcastReceiver() {
            private var mIgnoreFirst = false
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val info = intent!!.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)!!
                val conn = mMgr.connectionInfo

                Log.d(LOG, "Receiver $info ${mMgr.connectionInfo} ${mMgr.connectionInfo.supplicantState} ${info.detailedState} ${mMgr.connectionInfo.ipAddress} ${info.extraInfo}")
                if(!mIgnoreFirst) {
                    Log.d(LOG, "Receiver ignored")
                    mIgnoreFirst = true
                    return
                }

                if(info.state != NetworkInfo.State.CONNECTED || conn.supplicantState != SupplicantState.COMPLETED)
                    return

                val ssid = conn.ssid.trim('"')
                if(ssid == mSsid && (info.extraInfo.isNullOrEmpty() || !info.extraInfo.startsWith('"') || info.extraInfo == conn.ssid)) {
                    synchronized(WifiConnector) {
                        mNetworkId = conn.networkId
                    }
                    returnAndClose(true, null)
                } else {
                    returnAndClose(false, R.string.wifi_failed_to_enable)
                }
            }
        }
    }


    @SuppressLint("NewApi")
    private class AboveQ(
            mContext: Context,
            mListener: OnWifiConnectorDoneListener,
            mSsid: String,
            mPassword: String) : WifiConnectorBase(mContext, mListener, mSsid, mPassword) {

        private val mConnMgr = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        override fun connect() {
            super.connect()

            val spec = WifiNetworkSpecifier.Builder().apply {
                setSsid(mSsid)
                if(mPassword.isNotEmpty())
                    setWpa2Passphrase(mPassword)
            }.build()

            val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(spec)
                    .build()

            try {
                val callback = AboveQNetworkListener(this)
                mConnMgr.requestNetwork(req, callback)
            } catch(e: RuntimeException) {
                e.printStackTrace()
                returnAndClose(false, R.string.wifi_too_many_requests)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class AboveQNetworkListener(connector: AboveQ) : ConnectivityManager.NetworkCallback() {
        private val mConnector = WeakReference(connector)

        fun close(ctx: Context) {
            Log.d(LOG, "close AfterQNetworkListener")
            val connMgr = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connMgr.unregisterNetworkCallback(this)
        }

        override fun onAvailable(network: Network) {
            Log.d(LOG, "onAvailable")
            mNetworkListener = this
            mConnector.get()?.returnAndClose(true, null)
        }

        override fun onUnavailable() {
            Log.d(LOG, "onUnavailable")
            mConnector.get()?.returnAndClose(false, R.string.wifi_timed_out)
        }
    }

}
