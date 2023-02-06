package org.radarcns.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.splash.SplashActivity
import org.radarbase.android.util.Boast
import org.radarcns.detail.InfoActivity.Companion.PRIVACY_POLICY
import org.radarcns.detail.MainActivityBootStarter.Companion.BOOT_START_NOTIFICATION_ID
import org.radarcns.detail.databinding.ActivitySplashBinding

class SplashActivityImpl : SplashActivity() {
    override val delayMs: Long = 500L
    private var notifyResume = false
    private lateinit var binding: ActivitySplashBinding

    init {
        waitForFullFetchMs = 3_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.hasExtra("crash") == true) {
            notifyResume = true
        } else {
            (application as RadarApplicationImpl).enableCrashProcessing()
        }
        radarApp.notificationHandler.cancel(BOOT_START_NOTIFICATION_ID)
        addPrivacyPolicy(binding.splashPrivacyPolicyUrl)
    }

    override fun createView() {
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onDidStartActivity() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onWillStartActivity() = Unit

    override fun updateView() {
        if (notifyResume) {
            Boast.makeText(this, R.string.recovered_from_crash, LENGTH_LONG).show()
            notifyResume = false
        }
        binding.splashMessageText.setText(when (state) {
            STATE_INITIAL -> R.string.app_initializing
            STATE_STARTING, STATE_FINISHED -> R.string.app_starting
            STATE_AUTHORIZING -> R.string.app_authorizing
            STATE_FETCHING_CONFIG -> R.string.app_fetching_config
            STATE_DISCONNECTED -> R.string.app_disconnected
            STATE_FIREBASE_UNAVAILABLE -> R.string.firebase_unavailable
            else -> R.string.emptyText
        })
    }

    companion object {
        @MainThread
        fun AppCompatActivity.addPrivacyPolicy(view: TextView) {
            radarApp.configuration.config.observe(this) { config ->
                val privacyPolicyUrl = config.optString(PRIVACY_POLICY)
                if (privacyPolicyUrl != null) {
                    view.setText(R.string.privacy_policy)
                    view.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
                    }
                    view.visibility = VISIBLE
                } else {
                    view.visibility = GONE
                }
            }
        }
    }
}
