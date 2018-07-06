package com.tassadar.rbcontroller

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.*


class ControllerActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener, WebSocketServer.OnWebSocketMessageListener {
    companion object {
        const val LOG = "RB:ControllerAct"
    }

    var mServer: WebSocketServer? = null
    val mUdpHandler = UdpHandler(this)
    var mDevice :Device? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        mServer = WebSocketServer(InetSocketAddress("127.0.0.1", 9000), this)
        mServer?.isReuseAddr = true
        mServer?.start()

        val webview = findViewById<WebView>(R.id.webview)
        val settings = webview.settings
        settings.javaScriptEnabled = true
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webview.webViewClient = Client()

        webview.setOnLongClickListener { return@setOnLongClickListener true }
        webview.isLongClickable = false

        mDevice = intent.getParcelableExtra<Device>("device")

        if(savedInstanceState != null) {
            webview.restoreState(savedInstanceState)
        } else {
            webview.loadUrl("http://${mDevice!!.address.hostString}:${mDevice!!.port}${mDevice!!.path}")
        }
    }

    override fun onResume() {
        super.onResume()

        mUdpHandler.start()
    }

    override fun onPause() {
        super.onPause()
        mUdpHandler.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        findViewById<WebView>(R.id.webview).loadUrl("about:blank")
        mServer?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        findViewById<WebView>(R.id.webview).saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.activity_discover, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                val webview = findViewById<WebView>(R.id.webview)
                webview.clearCache(true)

                mUdpHandler.stop()
                mUdpHandler.resetPort()
                mUdpHandler.start()

                webview.reload()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onUdpPacket(addr: InetSocketAddress, cmd: String, data: JSONObject) {
        mServer?.broadcast(data.toString())
    }

    override fun onWebSocketMessage(message: String) {
        try {
            val data = JSONObject(message)
            val command = data.getString("c")
            mUdpHandler.send(mDevice?.address as SocketAddress, command, data)
        } catch(ex :JSONException) {
            Log.e(LOG, "invalid json received from the web", ex)
        }
    }

    inner class Client : WebViewClient() {
        private val mOverrides = setOf(
                "nipplejs.min.js",
                "reconnecting-websocket.min.js"
        )

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
