package org.radarcns.detail

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast.LENGTH_LONG
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.splash.SplashActivity
import org.radarbase.android.util.Boast
import org.radarbase.android.widget.addPrivacyPolicy
import org.radarbase.android.widget.repeatAnimation
import org.radarcns.detail.MainActivityBootStarter.Companion.BOOT_START_NOTIFICATION_ID
import org.radarcns.detail.databinding.ActivitySplashBinding

class SplashActivityImpl : SplashActivity() {
    override val delayMs: Long = 0L
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

        findViewById<ImageView>(R.id.splash_image).repeatAnimation()
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
}
