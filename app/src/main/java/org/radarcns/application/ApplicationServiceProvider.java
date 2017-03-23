package org.radarcns.application;

import android.os.Bundle;
import android.os.Parcelable;

import org.radarcns.R;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Collections;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class ApplicationServiceProvider extends DeviceServiceProvider<ApplicationState> {
    @Override
    public Class<?> getServiceClass() {
        return ApplicationStatusService.class;
    }

    @Override
    public Parcelable.Creator<ApplicationState> getStateCreator() {
        return ApplicationState.CREATOR;
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }

    @Override
    public List<String> needsPermissions() {
        return Collections.singletonList(WRITE_EXTERNAL_STORAGE);
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        this.getConfig().putExtras(bundle, RadarConfiguration.DEVICE_SERVICES_TO_CONNECT);
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.applicationServiceDisplayName);
    }
}
