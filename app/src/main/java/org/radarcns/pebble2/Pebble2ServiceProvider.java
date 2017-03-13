package org.radarcns.pebble2;

import android.os.Parcelable;

import org.radarcns.R;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;

public class Pebble2ServiceProvider extends DeviceServiceProvider<Pebble2DeviceStatus> {
    @Override
    public Class<?> getServiceClass() {
        return Pebble2Service.class;
    }

    @Override
    public Parcelable.Creator<Pebble2DeviceStatus> getStateCreator() {
        return Pebble2DeviceStatus.CREATOR;
    }

    @Override
    public boolean hasDetailView() {
        return true;
    }

    public void showDetailView() {
        new Pebble2HeartbeatToast(getActivity()).execute(getConnection());
    }

    @Override
    public List<String> needsPermissions() {
        return Arrays.asList(ACCESS_COARSE_LOCATION, BLUETOOTH, BLUETOOTH_ADMIN);
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.pebble2DisplayName);
    }
}
