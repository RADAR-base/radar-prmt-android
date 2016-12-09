package org.radarcns.android;

import android.os.Parcelable;

/** Current state of a wearable device. */
public interface DeviceState extends Parcelable {
    DeviceStatusListener.Status getStatus();

    /**
     * Get the battery level, between 0 (empty) and 1 (full).
     * @return battery level or Float.NaN if unknown.
     */
    float getBatteryLevel();

    /**
     * Get the temperature in degrees Celcius.
     * @return temperature or Float.NaN if unknown.
     */
    float getTemperature();

    /**
     * Get the heart rate in bpm.
     * @return heart rate or Float.NaN if unknown.
     */
    float getHeartRate();
}
