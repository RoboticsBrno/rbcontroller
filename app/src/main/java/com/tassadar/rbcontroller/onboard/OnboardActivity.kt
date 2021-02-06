package com.tassadar.rbcontroller.onboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tassadar.rbcontroller.R


class OnboardActivity : AppCompatActivity() {
    private var mCurrentFragmentIdx: Int = -1

    private val mFragments = ArrayList<Class<*>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mFragments.clear()
        if(!getSharedPreferences("", Context.MODE_PRIVATE).contains("owner")) {
            mFragments.add(FragmentSetupOwner::class.java)
        }

        if(packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
                ContextCompat.checkSelfPermission(this@OnboardActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mFragments.add(FragmentSetupBle::class.java)
        }

        if(mFragments.isEmpty()) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        supportActionBar?.hide()
        setContentView(R.layout.activity_onboard)

        switchFragment(0)
    }

    private fun switchFragment(idx: Int) {
        val tx = supportFragmentManager.beginTransaction()
        tx.replace(R.id.fragment_root, mFragments[idx].newInstance() as Fragment)
        tx.commit()

        mCurrentFragmentIdx = idx
    }

    fun onNextClicked(@Suppress("UNUSED_PARAMETER") view :View) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_root) as? FragmentBase
        if(fragment != null && !fragment.onNextClicked())
            return

        if(mCurrentFragmentIdx+1 >= mFragments.size) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            switchFragment(mCurrentFragmentIdx+1)
        }
    }
}
