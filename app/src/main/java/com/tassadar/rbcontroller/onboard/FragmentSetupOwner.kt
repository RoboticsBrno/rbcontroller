package com.tassadar.rbcontroller.onboard

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.tassadar.rbcontroller.AsciiInputFilter
import com.tassadar.rbcontroller.R

class FragmentSetupOwner : FragmentBase() {
    private var mRoot: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mRoot = inflater.inflate(R.layout.fragment_setup_owner, container, false)

        val logo = mRoot!!.findViewById<ImageView>(R.id.logo)

        val logoSet = AnimatorSet()
        logoSet.interpolator = OvershootInterpolator()
        logoSet.duration = 1000
        logoSet.startDelay = 200
        val builder = logoSet.play(ObjectAnimator.ofFloat(logo, "rotation", 0.0f, 360.0f))
                .with(ObjectAnimator.ofFloat(logo, "scaleX", 0.0f, 1.0f))
                .with(ObjectAnimator.ofFloat(logo, "scaleY", 0.0f, 1.0f))

        val ids = intArrayOf(R.id.robotarna_text, R.id.owner_edit, R.id.owner_text)
        val animators = Array<Animator?>(ids.size) { null }
        ids.forEachIndexed { i, id ->
            val view = mRoot!!.findViewById<View>(id)
            animators[i] = ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f)
        }

        val alphaSet = AnimatorSet()
        alphaSet.interpolator = AccelerateInterpolator()
        alphaSet.duration = 500
        alphaSet.playTogether(*animators)

        builder.before(alphaSet)
        logoSet.start()

        val ed = mRoot!!.findViewById<EditText>(R.id.owner_edit)
        ed.addTextChangedListener(mTextWatcher)
        ed.filters = arrayOf(AsciiInputFilter())

        return mRoot
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mRoot = null
    }

    private val mTextWatcher = object : TextWatcher {
        override fun afterTextChanged(text: Editable?) {
            activity?.findViewById<Button>(R.id.next_button)?.apply {
                isEnabled = text!!.length in 3..30
            }
        }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    override fun onNextClicked(): Boolean {
        val owner = mRoot!!.findViewById<EditText>(R.id.owner_edit).text.toString()
        val e = activity!!.getSharedPreferences("", Context.MODE_PRIVATE).edit()
        e.putString("owner", owner)
        e.apply()
        return true
    }
}
