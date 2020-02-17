package com.tassadar.rbcontroller.onboard

import androidx.fragment.app.Fragment

abstract class FragmentBase : Fragment() {
    open fun onNextClicked(): Boolean {
        return true
    }
}
