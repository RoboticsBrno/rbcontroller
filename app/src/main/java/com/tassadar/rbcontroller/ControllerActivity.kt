package com.tassadar.rbcontroller

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.*


class ControllerActivity : AppCompatActivity() {
    var mServer: WebSocketServer? = null
    val mUdpHandler = UdpHandler()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        mServer = WebSocketServer(InetSocketAddress("127.0.0.1", 9000))
        mServer?.isReuseAddr = true
        mServer?.start()

        val webview = findViewById<WebView>(R.id.webview)
        val settings = webview.settings
        settings.javaScriptEnabled = true
        webview.webViewClient = WebViewClient()

        webview.loadUrl("http://192.168.100.100/test/index.html")

        mUdpHandler.start()

        mUdpHandler.broadcast("discover")
    }

    override fun onDestroy() {
        super.onDestroy()

        mServer?.stop()
        mUdpHandler.stop()
    }
}
