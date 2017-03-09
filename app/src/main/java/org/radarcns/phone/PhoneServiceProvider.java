package org.radarcns.phone;

import android.os.Parcelable;

import org.radarcns.R;
import org.radarcns.android.RadarServiceProvider;

public class PhoneServiceProvider extends RadarServiceProvider<PhoneState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneSensorsService.class;
    }

    @Override
    public Parcelable.Creator<PhoneState> getStateCreator() {
        return PhoneState.CREATOR;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneServiceDisplayName);
    }
}
