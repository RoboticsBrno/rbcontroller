package com.tassadar.rbcontroller

import android.text.InputFilter
import android.text.Spanned

class AsciiInputFilter : InputFilter {
    override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence {
        val sb = StringBuilder()
        for(i in start until end) {
            val c = source!![i].toInt()
            if(c in 1..127) {
                sb.append(source[i])
            }
        }
        return sb.toString()
    }
}