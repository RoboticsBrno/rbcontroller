package com.tassadar.rbcontroller

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.SocketAddress


class ControllerActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener, WebSocketServer.OnWebSocketMessageListener {
    companion object {
        const val LOG = "RB:ControllerAct"
    }

    private var mServer: WebSocketServer? = null
    private val mUdpHandler = UdpHandler(this)
    private val mGravitySensor: GravitySensor by lazy(LazyThreadSafetyMode.NONE) {
        GravitySensor(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }

    private val mDevice: Device.WiFi by lazy(LazyThreadSafetyMode.NONE) {
        intent.getParcelableExtra<Device.WiFi>("device")!!
    }
    private val mDeviceAddress: SocketAddress by lazy(LazyThreadSafetyMode.NONE) {
        InetSocketAddress(mDevice.address, UdpHandler.BROADCAST_PORT)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setContentView(R.layout.activity_controller)

        mUdpHandler.start(this)

        mServer = WebSocketServer(InetSocketAddress("127.0.0.1", 9000), this)
        mServer?.isReuseAddr = true
        mServer?.start()

        val webview = findViewById<WebView>(R.id.webview)
        webview.settings.apply {
            javaScriptEnabled = true
            userAgentString = "RBController ${BuildConfig.VERSION_NAME}"
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                saveFormData = false
            }
        }

        webview.webViewClient = Client()
        webview.webChromeClient = object: WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.i("WebViewConsole", consoleMessage.message())
                return true
            }
        }

        webview.setOnLongClickListener { return@setOnLongClickListener true }
        webview.isLongClickable = false
        webview.addJavascriptInterface(mGravitySensor, "RbGravitySensor")

        if(savedInstanceState != null) {
            webview.restoreState(savedInstanceState)
        } else {
            webview.loadUrl("http://${mDevice.address.hostAddress}:${mDevice.port}${mDevice.path}")
        }
    }

    override fun onResume() {
        super.onResume()

        mUdpHandler.start(this)
        mGravitySensor.onResume()
    }

    override fun onPause() {
        super.onPause()
        mUdpHandler.stop()
        mGravitySensor.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        findViewById<WebView>(R.id.webview).loadUrl("about:blank")
        mServer?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        findViewById<WebView>(R.id.webview).saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.activity_controller, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                val webview = findViewById<WebView>(R.id.webview)
                webview.clearCache(true)
                WebStorage.getInstance().deleteAllData()

                mUdpHandler.stop()
                mUdpHandler.resetPort()
                mUdpHandler.start(this)

                mGravitySensor.stop()

                webview.reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onUdpPacket(addr: InetSocketAddress, cmd: String, data: JSONObject) {
        mServer?.broadcast(data.toString())
    }

    override fun onWebSocketMessage(message: String) {
        try {
            val data = JSONObject(message)
            val command = data.getString("c")
            mUdpHandler.send(mDeviceAddress, command, data)
        } catch(ex :JSONException) {
            Log.e(LOG, "invalid json received from the web", ex)
        }
    }

    inner class Client : WebViewClient() {
        private val mOverrides = setOf(
                "nipplejs.min.js",
                "reconnecting-websocket.min.js"
        )

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view!!.clearFocus()
            view.requestFocus()
        }

        /*@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): WebResourceResponse? {
            for((k,v) in req!!.requestHeaders) {
                Log.e(LOG, "Header ${v.length} $k $v")
            }
            return super.shouldInterceptRequest(view, req)
        }*/

        @Deprecated("Deprecated in Java")
        override fun shouldInterceptRequest(view: WebView?, url :String): WebResourceResponse? {
            val idx = url.lastIndexOf("/")
            if(idx == -1){
                return null
            }
            val filename = url.substring(idx+1)

            if(filename == "favicon.ico") {
                Log.i(LOG, "Overriding request $url")
                val str = ByteArrayInputStream(ByteArray(0))
                return WebResourceResponse("image/png", "UTF-8", str)
            } else if(mOverrides.contains(filename)) {
                Log.i(LOG, "Overriding request $url")
                val str = assets.open(filename)
                return WebResourceResponse("application/javascript", "UTF-8", str)
            }
            return null
        }
    }
}
