package org.radarcns.android;

import android.os.Parcelable;

public interface DeviceState extends Parcelable {
    DeviceStatusListener.Status getStatus();
}
