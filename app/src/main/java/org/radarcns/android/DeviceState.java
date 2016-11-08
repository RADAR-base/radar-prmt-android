package org.radarcns.android;

import android.os.Parcelable;

/** Current state of a wearable device. */
public interface DeviceState extends Parcelable {
    DeviceStatusListener.Status getStatus();
}
