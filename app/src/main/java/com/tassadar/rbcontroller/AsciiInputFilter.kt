package com.tassadar.rbcontroller

import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils


class AsciiInputFilter : InputFilter {
    override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
        var keepOriginal = true
        val sb = StringBuilder(end - start)
        for (i in start until end) {
            val c = source!![i].toInt()
            if (c in 1..127)
                sb.append(c)
            else
                keepOriginal = false
        }

        return if (keepOriginal) {
            null
        } else {
            if (source is Spanned) {
                val sp = SpannableString(sb)
                TextUtils.copySpansFrom(source, start, end, null, sp, 0)
                sp
            } else {
                sb
            }
        }
    }
}
