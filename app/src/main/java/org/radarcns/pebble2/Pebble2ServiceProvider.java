package org.radarcns.pebble2;

import android.os.Parcelable;

import org.radarcns.R;
import org.radarcns.android.RadarServiceProvider;

public class Pebble2ServiceProvider extends RadarServiceProvider<Pebble2DeviceStatus> {
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
    public String getDisplayName() {
        return getActivity().getString(R.string.pebble2DisplayName);
    }
}
