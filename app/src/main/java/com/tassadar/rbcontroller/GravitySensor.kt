package com.tassadar.rbcontroller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.webkit.JavascriptInterface

class GravitySensor(val mSensorMgr: SensorManager) : SensorEventListener {
    private var mYaw = 0.0
    private var mPitch = 0.0
    private var mRoll = 0.0

    private var mStarted = false
    private var mRegistered = false

    @JavascriptInterface
    fun getYaw(): Double = mYaw

    @JavascriptInterface
    fun getPitch(): Double = mPitch

    @JavascriptInterface
    fun getRoll(): Double = mRoll

    @JavascriptInterface
    fun start() {
        if(!mStarted) {
            mStarted = true
            onResume()
        }
    }

    @JavascriptInterface
    fun stop() {
        if(mStarted) {
            mStarted = false
            onPause()
        }
    }

    fun onResume() {
        if(!mStarted || mRegistered)
            return

        val grav = mSensorMgr.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if(grav != null) {
            mSensorMgr.registerListener(this, grav, 50000)
            mRegistered = true
        }
    }

    fun onPause() {
        if(mRegistered) {
            mRegistered = false
            mSensorMgr.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ax = event!!.values[0] / SensorManager.STANDARD_GRAVITY
        val ay = event.values[1] / SensorManager.STANDARD_GRAVITY
        val az = event.values[2] / SensorManager.STANDARD_GRAVITY
        mYaw = Math.atan(ay / Math.sqrt(ax * ax + az * az.toDouble()))
        mPitch = Math.atan(az / Math.sqrt(ax * ax + ay * ay.toDouble()))
        mRoll = Math.atan(ax / Math.sqrt(ay * ay + az * az.toDouble()))
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

}