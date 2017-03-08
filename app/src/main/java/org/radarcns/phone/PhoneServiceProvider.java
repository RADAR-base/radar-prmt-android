package org.radarcns.phone;

import android.os.Parcelable;

import org.radarcns.android.RadarServiceProvider;
import org.radarcns.empaticaE4.E4Service;

public class PhoneServiceProvider extends RadarServiceProvider<PhoneState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneSensorsService.class;
    }

    @Override
    public Parcelable.Creator<PhoneState> getStateCreator() {
        return PhoneState.CREATOR;
    }
}
