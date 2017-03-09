package org.radarcns.application;

import android.os.Parcelable;

import org.radarcns.R;
import org.radarcns.android.RadarServiceProvider;

/**
 * Created by joris on 07/03/2017.
 */

public class ApplicationServiceProvider extends RadarServiceProvider<ApplicationState> {
    @Override
    public Class<?> getServiceClass() {
        return ApplicationStatusService.class;
    }

    @Override
    public Parcelable.Creator<ApplicationState> getStateCreator() {
        return ApplicationState.CREATOR;
    }

    public boolean isDisplayable() {
        return false;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.applicationServiceDisplayName);
    }
}
