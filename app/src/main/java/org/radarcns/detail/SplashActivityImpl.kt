package org.radarcns.detail

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast.LENGTH_LONG
import kotlinx.android.synthetic.main.activity_splash.*
import org.radarbase.android.splash.SplashActivity
import org.radarbase.android.util.Boast

class SplashActivityImpl : SplashActivity() {
    private lateinit var messageText: TextView
    override val delayMs: Long = 500L
    private var notifyResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.hasExtra("crash") == true) {
            notifyResume = true
        } else {
            (application as RadarApplicationImpl).enableCrashProcessing()
        }
    }

    override fun createView() {
        setContentView(R.layout.activity_splash)

        messageText = splashMessageText
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
        messageText.setText(when (state) {
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
