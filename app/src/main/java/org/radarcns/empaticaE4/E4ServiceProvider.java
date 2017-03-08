package org.radarcns.empaticaE4;

import android.os.Bundle;
import android.os.Parcelable;

import org.radarcns.RadarConfiguration;
import org.radarcns.android.RadarServiceProvider;

public class E4ServiceProvider extends RadarServiceProvider<E4DeviceStatus> {
    @Override
    public Class<?> getServiceClass() {
        return E4Service.class;
    }

    @Override
    public Parcelable.Creator<E4DeviceStatus> getStateCreator() {
        return E4DeviceStatus.CREATOR;
    }

    @Override
    public boolean hasDetailView() {
        return true;
    }

    public void showDetailView() {
        new E4HeartbeatToast(getActivity()).execute(getConnection());
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        getConfig().putExtras(bundle, RadarConfiguration.EMPATICA_API_KEY);
    }
}
