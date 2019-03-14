package org.radarbase.passive.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import org.radarbase.android.RadarApplication

class InfoActivity : AppCompatActivity() {
    private var policyUrl: String? = null

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setContentView(R.layout.compact_info)

        findViewById<TextView>(R.id.app_name).setText(R.string.app_name)
        findViewById<TextView>(R.id.version).text = BuildConfig.VERSION_NAME

        policyUrl = (application as RadarApplication).configuration.optString(PRIVACY_POLICY)

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

    fun showLicenses(view: View) {
        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }

    fun openPrivacyPolicy(view: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
    }

    companion object {
        const val PRIVACY_POLICY = "privacy_policy"
    }
}
