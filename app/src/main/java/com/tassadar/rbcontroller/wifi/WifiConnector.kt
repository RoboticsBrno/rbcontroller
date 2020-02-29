package com.tassadar.rbcontroller.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
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
import com.tassadar.rbcontroller.R
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.timerTask


object WifiConnector {
    interface OnWifiConnectorDoneListener {
        fun onWifiConnectorDone(success: Boolean, errorStringId: Int?)
    }

    private const val LOG = "RB:WifiConnector"

    private var mNetworkListener: AfterQNetworkListener? = null

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

    fun releaseNetwork(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mNetworkListener?.close(ctx)
            mNetworkListener = null
        }
    }

    private abstract class WifiConnectorBase(
            protected val mContext: Context,
            protected val mListener: OnWifiConnectorDoneListener,
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
                        mListener.onWifiConnectorDone(false, null)
                        close()
                    }
                    .show()
        }

        protected fun returnAndClose(success: Boolean, errorStringId: Int?) {
            close()
            mListener.onWifiConnectorDone(success, errorStringId)
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

            val filter = IntentFilter()
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            mContext.registerReceiver(mReceiver, filter)

            if (!mMgr.enableNetwork(networkId, true)) {
                returnAndClose(false, R.string.wifi_failed_to_enable)
                return
            }

            mTimer.schedule(timerTask {
                returnAndClose(false, R.string.wifi_timed_out)
            }, 30000)
        }

        private val mReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val info = intent!!.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)!!
                val conn = mMgr.connectionInfo

                Log.d(LOG, "Receiver $info ${mMgr.connectionInfo} ${mMgr.connectionInfo.supplicantState} ${info.detailedState} ${mMgr.connectionInfo.ipAddress} ${info.extraInfo}")

                if(info.state != NetworkInfo.State.CONNECTED || conn.supplicantState != SupplicantState.COMPLETED)
                    return

                val ssid = conn.ssid.trim('"')
                if(ssid != mSsid)
                    return

                if(!info.extraInfo.isNullOrEmpty() && info.extraInfo.startsWith('"') && info.extraInfo != conn.ssid)
                    return

                returnAndClose(true, null)
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
                    .setNetworkSpecifier(spec)
                    .build()

            try {
                val callback = AfterQNetworkListener(this, mListener)
                mConnMgr.requestNetwork(req, callback, 30000)
            } catch(e: RuntimeException) {
                e.printStackTrace()
                returnAndClose(false, R.string.wifi_too_many_requests)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class AfterQNetworkListener(connector: AboveQ, listener: OnWifiConnectorDoneListener) : ConnectivityManager.NetworkCallback() {
        private val mConnector = WeakReference(connector)
        private val mListener = WeakReference(listener)

        fun close(ctx: Context) {
            val connMgr = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connMgr.unregisterNetworkCallback(this)
        }

        override fun onAvailable(network: Network) {
            Log.d(LOG, "onAvailable $network")
            mListener.get()?.onWifiConnectorDone(true, null)
            mConnector.get()?.close()
            mNetworkListener = this
        }

        override fun onUnavailable() {
            mListener.get()?.onWifiConnectorDone(false, R.string.wifi_timed_out)
            mConnector.get()?.close()
        }
    }

}
