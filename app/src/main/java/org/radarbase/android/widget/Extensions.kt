package org.radarbase.android.widget

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarcns.detail.InfoActivity
import org.radarcns.detail.R

@MainThread
fun ImageView.repeatAnimation() {
    val avd = drawable as? AnimatedVectorDrawable ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        avd.registerAnimationCallback(
            object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    post { avd.start() }
                }
            }
        )
    }
    avd.start()
}

@MainThread
fun AppCompatActivity.addPrivacyPolicy(view: TextView) {
    radarApp.configuration.config.observe(this) { config ->
        val privacyPolicyUrl = config.optString(InfoActivity.PRIVACY_POLICY)
        if (privacyPolicyUrl != null) {
            view.setText(R.string.privacy_policy)
            view.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
            }
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }
}
