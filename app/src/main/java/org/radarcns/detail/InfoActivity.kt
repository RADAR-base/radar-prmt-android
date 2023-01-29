package org.radarcns.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration.Companion.BASE_URL_KEY

class InfoActivity : AppCompatActivity() {
    private var policyUrl: String? = null

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setContentView(R.layout.compact_info)

        findViewById<TextView>(R.id.app_name).setText(R.string.app_name)
        findViewById<TextView>(R.id.version).text = BuildConfig.VERSION_NAME

        radarConfig.config.observe(this) { config ->
            findViewById<TextView>(R.id.server_base_url).text = config.getString(BASE_URL_KEY, "")

            policyUrl = config.optString(PRIVACY_POLICY)

            if (policyUrl == null) {
                findViewById<TextView>(R.id.generalPrivacyPolicyStatement).apply {
                    visibility = View.GONE
                }
            }
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        findViewById<TextView>(R.id.generalPrivacyPolicyStatement).setOnClickListener { v -> openPrivacyPolicy(v) }
        findViewById<TextView>(R.id.licenses_button).setOnClickListener { v -> showLicenses(v) }
    }

    private fun showLicenses(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }

    private fun openPrivacyPolicy(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
    }

    companion object {
        const val PRIVACY_POLICY = "privacy_policy"
    }
}
