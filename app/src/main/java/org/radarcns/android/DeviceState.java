package org.radarcns.android;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.key.MeasurementKey;

/** Current state of a wearable device. */
public abstract class DeviceState implements Parcelable {
    private final MeasurementKey id = new MeasurementKey(null, null);
    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;

    public DeviceStatusListener.Status getStatus() {
        return status;
    }

    public MeasurementKey getId() {
        return id;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id.getUserId());
        dest.writeString(id.getSourceId());
        dest.writeInt(status.ordinal());
    }

    protected void updateFromParcel(Parcel in) {
        id.setUserId(in.readString());
        id.setSourceId(in.readString());
        status = DeviceStatusListener.Status.values()[in.readInt()];
    }

    public synchronized void setStatus(DeviceStatusListener.Status status) {
        this.status = status;
    }

    /**
     * Get the battery level, between 0 (empty) and 1 (full).
     * @return battery level or Float.NaN if unknown.
     */
    public float getBatteryLevel()  {
        return Float.NaN;
    }

    /**
     * Get the temperature in degrees Celcius.
     * @return temperature or Float.NaN if unknown.
     */
    public float getTemperature() {
        return Float.NaN;
    }

    /**
     * Get the heart rate in bpm.
     * @return heart rate or Float.NaN if unknown.
     */
    public float getHeartRate() {
        return Float.NaN;
    }
}
