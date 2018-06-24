package com.tassadar.rbcontroller

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.*


class ControllerActivity : AppCompatActivity(), UdpHandler.OnUdpPacketListener {
    var mServer: WebSocketServer? = null
    val mUdpHandler = UdpHandler(this)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        mServer = WebSocketServer(InetSocketAddress("127.0.0.1", 9000))
        mServer?.isReuseAddr = true
        mServer?.start()

        val webview = findViewById<WebView>(R.id.webview)
        val settings = webview.settings
        settings.javaScriptEnabled = true
        webview.webViewClient = WebViewClient()

        val dev = intent.getParcelableExtra<Device>("device")

        webview.loadUrl("http://${dev.address.hostString}:${dev.port}${dev.path}")
    }

    override fun onDestroy() {
        super.onDestroy()

        mServer?.stop()
        mUdpHandler.stop()
    }
    override fun onUdpPacket(addr: InetSocketAddress, cmd: String, data: JSONObject) {

    }
}
