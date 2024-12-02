package org.radarbase.android.widget

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarcns.detail.InfoActivity
import org.radarcns.detail.R

suspend fun ImageView.repeatAnimation() {
    withContext(Dispatchers.Main) {
        val avd = drawable as? AnimatedVectorDrawable ?: return@withContext
        avd.registerAnimationCallback(object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                post { avd.start() }
            }
        })
        avd.start()
    }
}

suspend fun AppCompatActivity.addPrivacyPolicy(view: TextView, activityScope: CoroutineScope) {
    activityScope.launch(Dispatchers.Main) {
        radarApp.configuration.config.collect { config ->
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
}

fun String.toUrlOrNull(): Url? {
    return try {
        Url(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

