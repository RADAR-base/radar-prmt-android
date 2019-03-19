package org.radarbase.passive.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import org.radarbase.android.RadarConfiguration.Companion.BASE_URL_KEY
import org.radarbase.android.radarConfig

class InfoActivity : AppCompatActivity() {
    private var policyUrl: String? = null

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setContentView(R.layout.compact_info)

        findViewById<TextView>(R.id.app_name).setText(R.string.app_name)
        findViewById<TextView>(R.id.version).text = BuildConfig.VERSION_NAME
        findViewById<TextView>(R.id.server_base_url).text = radarConfig.getString(BASE_URL_KEY, "")

        policyUrl = radarConfig.optString(PRIVACY_POLICY)

        if (policyUrl == null) {
            findViewById<TextView>(R.id.privacyStatement).apply {
                visibility = View.GONE
            }
        }

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.info)
        })
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    fun showLicenses(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }

    fun openPrivacyPolicy(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
    }

    companion object {
        const val PRIVACY_POLICY = "privacy_policy"
    }
}
