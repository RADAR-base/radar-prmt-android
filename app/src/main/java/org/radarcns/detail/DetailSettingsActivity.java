package org.radarcns.detail;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.Toolbar;

import org.radarcns.android.RadarConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DetailSettingsActivity extends Activity {
    private static final Logger logger = LoggerFactory.getLogger(DetailSettingsActivity.class);

    private Switch enableDataButton;
    private Switch enableDataPriorityButton;

    private static final String[] MANAGED_SETTINGS = {
            RadarConfiguration.SEND_ONLY_WITH_WIFI,
            RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY
    };

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.settings);
        setActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getActionBar());
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        enableDataButton = findViewById(R.id.enableDataSwitch);
        enableDataButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RadarConfiguration.getInstance().put(RadarConfiguration.SEND_ONLY_WITH_WIFI, !isChecked);
            logger.info("Configuration updated: {}", RadarConfiguration.getInstance());
            enableDataPriorityButton.setEnabled(isChecked);
        });
        enableDataPriorityButton = findViewById(R.id.enableDataHighPrioritySwitch);
        enableDataPriorityButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RadarConfiguration.getInstance().put(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, isChecked);
            logger.info("Configuration updated: {}", RadarConfiguration.getInstance());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateView();
    }

    private void updateView() {
        RadarConfiguration config = RadarConfiguration.getInstance();
        enableDataButton.setChecked(!config.getBoolean(RadarConfiguration.SEND_ONLY_WITH_WIFI, RadarConfiguration.SEND_ONLY_WITH_WIFI_DEFAULT));
        enableDataPriorityButton.setChecked(config.getBoolean(RadarConfiguration.SEND_OVER_DATA_HIGH_PRIORITY, RadarConfiguration.SEND_ONLY_WITH_WIFI_DEFAULT));
    }

    public void startReset(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Reset")
                .setMessage("Do you really want to reset to default settings?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    RadarConfiguration.getInstance().reset(MANAGED_SETTINGS);
                    updateView();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }
}
