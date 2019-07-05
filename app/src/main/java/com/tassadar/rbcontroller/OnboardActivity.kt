package com.tassadar.rbcontroller

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class OnboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_onboard)

        val logo = findViewById<ImageView>(R.id.logo)

        val logoSet = AnimatorSet()
        logoSet.interpolator = OvershootInterpolator()
        logoSet.duration = 1000
        logoSet.startDelay = 200
        val builder = logoSet.play(ObjectAnimator.ofFloat(logo, "rotation", 0.0f, 360.0f))
                .with(ObjectAnimator.ofFloat(logo, "scaleX", 0.0f, 1.0f))
                .with(ObjectAnimator.ofFloat(logo, "scaleY", 0.0f, 1.0f))

        val ids = intArrayOf(R.id.robotarna_text, R.id.owner_edit, R.id.owner_text, R.id.save_button)
        val animators = Array<Animator?>(ids.size) { i -> null };
        ids.forEachIndexed { i, id ->
            val view = findViewById<View>(id)
            animators[i] = ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f)
        }

        val alphaSet = AnimatorSet()
        alphaSet.interpolator = AccelerateInterpolator()
        alphaSet.duration = 500
        alphaSet.playTogether(*animators)

        builder.before(alphaSet)
        logoSet.start()

        val ed = findViewById<EditText>(R.id.owner_edit)
        ed.addTextChangedListener(mTextWatcher)
        ed.filters = arrayOf(AsciiInputFilter())
    }

    private val mTextWatcher = object : TextWatcher {
        override fun afterTextChanged(text: Editable?) {
            findViewById<Button>(R.id.save_button).isEnabled = text!!.length in 3..30
        }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    fun onSaveClicked(view :View) {
        val owner = findViewById<EditText>(R.id.owner_edit).text.toString()
        val e = getSharedPreferences("", Context.MODE_PRIVATE).edit()
        e.putString("owner", owner)
        e.apply()

        val intent = Intent()
        intent.putExtra("owner", owner)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
