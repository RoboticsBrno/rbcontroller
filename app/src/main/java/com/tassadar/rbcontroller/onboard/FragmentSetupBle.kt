package com.tassadar.rbcontroller.onboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.tassadar.rbcontroller.R

class FragmentSetupBle : FragmentBase() {
    private var mRoot: View? = null
    private var mRequested = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mRoot = inflater.inflate(R.layout.fragment_setup_ble, container, false)

        val logoRb = mRoot!!.findViewById<ImageView>(R.id.logo_rb)
        val logoBt = mRoot!!.findViewById<ImageView>(R.id.logo_bt)

        val logoRbSet = AnimatorSet().apply {
            interpolator = OvershootInterpolator()
            duration = 800
            startDelay = 200

            play(ObjectAnimator.ofFloat(logoRb, "rotation", 0.0f, 360.0f))
                    .with(ObjectAnimator.ofFloat(logoRb, "scaleX", 0.0f, 1.0f))
                    .with(ObjectAnimator.ofFloat(logoRb, "scaleY", 0.0f, 1.0f))
        }

        val logoBtSet = AnimatorSet().apply {
            interpolator = OvershootInterpolator(15.0f)
            duration = 500
            playTogether(ObjectAnimator.ofFloat(logoBt, "scaleX", 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(logoBt, "scaleY", 0.0f, 1.0f))
        }

        val bleText = mRoot!!.findViewById<TextView>(R.id.onboard_ble_text)
        bleText.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(getString(R.string.onboard_ble), 0)
        } else {
            Html.fromHtml(getString(R.string.onboard_ble))
        }

        val textAnim = ObjectAnimator
                .ofFloat(bleText, "alpha", 0.0f, 1.0f)
        textAnim.interpolator = AccelerateInterpolator()
        textAnim.duration = 500

        AnimatorSet().apply {
            play(logoRbSet)
                .before(textAnim)
                .before(logoBtSet)
        }.start()
        return mRoot
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.findViewById<Button>(R.id.next_button)?.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mRoot = null
    }

    override fun onNextClicked(): Boolean {
        if(mRequested) {
            return true
        }

        mRequested = true
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        (activity as? OnboardActivity)?.onNextClicked(mRoot!!)
    }

}
