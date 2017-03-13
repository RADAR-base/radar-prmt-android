package org.radarcns.application;

import android.os.Parcelable;

import org.radarcns.R;
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
    public String getDisplayName() {
        return getActivity().getString(R.string.applicationServiceDisplayName);
    }
}
