package org.radarcns.detail;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toolbar;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import org.radarcns.android.RadarConfiguration;

import java.util.Objects;

public class DetailInfoActivity extends Activity {
    public static final String PRIVACY_POLICY = "privacy_policy";
    private String policyUrl;

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.compact_info);

        ((TextView)findViewById(R.id.app_name)).setText(R.string.app_name);
        ((TextView)findViewById(R.id.version)).setText(BuildConfig.VERSION_NAME);

        policyUrl = RadarConfiguration.getInstance().getString(PRIVACY_POLICY, null);

        if (policyUrl == null) {
            TextView privacyPolicy = findViewById(R.id.privacyStatement);
            privacyPolicy.setVisibility(View.GONE);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.info);
        setActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getActionBar());
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    public void showLicenses(View view) {
        startActivity(new Intent(this, OssLicensesMenuActivity.class));
    }

    public void openPrivacyPolicy(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)));
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }
}
