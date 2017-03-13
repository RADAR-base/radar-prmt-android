package org.radarcns;

import org.radarcns.android.MainActivity;
import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

public class DetailMainActivity extends MainActivity {
    @Override
    protected RadarConfiguration createConfiguration() {
        // TODO: turn off developer mode
        return RadarConfiguration.configure(this, true, R.xml.remote_config_defaults);
    }

    @Override
    protected MainActivityView createView() {
        return new DetailMainActivityView(this, getRadarConfiguration());
    }

    @Override
    protected List<String> getActivityPermissions() {
        List<String> superPermissions = super.getActivityPermissions();
        List<String> result = new ArrayList<>(superPermissions.size() + 1);
        result.addAll(superPermissions);
        result.add(RECEIVE_BOOT_COMPLETED);
        return result;
    }
}
