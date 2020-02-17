package com.tassadar.rbcontroller.wifi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Spinner
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import com.tassadar.rbcontroller.Device
import com.tassadar.rbcontroller.R


class WifiConfigEdit : AppCompatActivity() {
    private val mDevice: Device by lazy(LazyThreadSafetyMode.NONE) {
        intent.getParcelableExtra("device") as Device
    }

    private var mLastWifiName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_config_edit)

        supportActionBar?.apply {
            title = getString(R.string.wifi_config_title, mDevice.name)
            setHomeAsUpIndicator(R.drawable.ic_close)
            setDisplayHomeAsUpEnabled(true)
        }

        loadConfig(getInitialConfig())

        findViewById<RadioButton>(R.id.mode_station).setOnCheckedChangeListener { _, checked ->
            updateEnabledViews(checked)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_wifi_config_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val res = Intent()
        intent.getParcelableExtra<Device.WifiConfig>(WifiConfigList.EXTRA_CONFIG_CURRENT)?.let {
            res.putExtra(WifiConfigList.EXTRA_CONFIG_ORIGINAL, it)
        }

        return when (item.itemId) {
            R.id.save -> {
                res.putExtra(WifiConfigList.EXTRA_CONFIG_CURRENT, getCfgFromViews())
                setResult(Activity.RESULT_OK, res)
                finish()
                true
            }
            R.id.delete -> {
                res.putExtra(WifiConfigList.EXTRA_DELETE, true)
                setResult(Activity.RESULT_OK, res)
                finish()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @UiThread
    private fun getInitialConfig(): Device.WifiConfig {
        intent.getParcelableExtra<Device.WifiConfig>(WifiConfigList.EXTRA_CONFIG_CURRENT)?.let {
            return it
        }

        val connInfo = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
        val name = if(connInfo.networkId != -1) connInfo.ssid.trim('"') else ""

        return Device.WifiConfig(true, name, "", 1)
    }

    @UiThread
    private fun loadConfig(cfg: Device.WifiConfig) {
        updateEnabledViews(cfg.stationMode)
        if(cfg.stationMode) {
            findViewById<RadioButton>(R.id.mode_station).isChecked = true
            findViewById<EditText>(R.id.name).apply {
                setText(cfg.name)
            }
            findViewById<EditText>(R.id.password).setText(cfg.password)
        } else {
            findViewById<RadioButton>(R.id.mode_ap).isChecked = true
            findViewById<EditText>(R.id.password).setText(cfg.password)
            findViewById<Spinner>(R.id.channel).apply {
                setSelection((cfg.channel - 1).coerceIn(0, count-1))
            }
        }
    }

    @UiThread
    private fun updateEnabledViews(stationMode: Boolean) {
        if(stationMode) {
            findViewById<EditText>(R.id.name).apply {
                isEnabled = true
                setText(mLastWifiName)
            }
        } else {
            findViewById<EditText>(R.id.name).apply {
                isEnabled = false
                mLastWifiName = text.toString()
                setText(mDevice.wifiApName)
            }
        }
        findViewById<Spinner>(R.id.channel).isEnabled = !stationMode
        findViewById<ImageView>(R.id.ic_mode).apply {
            setImageResource(if (stationMode) R.drawable.ic_wifi else R.drawable.ic_wifi_ap)
            setColorFilter(if (stationMode)
                resources.getColor(R.color.colorPrimary) else
                resources.getColor(R.color.colorAccent))
        }
    }

    @UiThread
    private fun getCfgFromViews(): Device.WifiConfig {
        return Device.WifiConfig(
                findViewById<RadioButton>(R.id.mode_station).isChecked,
                findViewById<EditText>(R.id.name).text.toString(),
                findViewById<EditText>(R.id.password).text.toString(),
                findViewById<Spinner>(R.id.channel).selectedItemPosition + 1)
    }
}
