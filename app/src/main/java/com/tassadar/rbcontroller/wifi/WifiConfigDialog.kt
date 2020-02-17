package com.tassadar.rbcontroller.wifi

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.tassadar.rbcontroller.R

class WifiConfigDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        return AlertDialog.Builder(requireActivity())
                .setTitle("SuperRuka's WiFi config")
                .setView(inflater.inflate(R.layout.activity_wifi_config_edit, null))
                .create()
    }
}
