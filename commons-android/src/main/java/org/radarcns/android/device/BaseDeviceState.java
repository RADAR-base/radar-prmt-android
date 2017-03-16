package org.radarcns.android.device;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.key.MeasurementKey;

/** Current state of a wearable device. */
public class BaseDeviceState implements Parcelable {
    public static final Parcelable.Creator<BaseDeviceState> CREATOR = new DeviceStateCreator<>(BaseDeviceState.class);

    private final MeasurementKey id = new MeasurementKey(null, null);
    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;

    public DeviceStatusListener.Status getStatus() {
        return status;
    }

    public MeasurementKey getId() {
        return id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id.getUserId());
        dest.writeString(id.getSourceId());
        dest.writeInt(status.ordinal());
    }

    public void updateFromParcel(Parcel in) {
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
     * Whether the state will gather any temperature information. This implementation returns false.
     */
    public boolean hasTemperature() {
        return false;
    }

    /**
     * Get the temperature in degrees Celcius.
     * @return temperature or Float.NaN if unknown.
     */
    public float getTemperature() {
        return Float.NaN;
    }

    /**
     * Whether the state will gather any heart rate information. This implementation returns false.
     */
    public boolean hasHeartRate() {
        return false;
    }

    /**
     * Get the heart rate in bpm.
     * @return heart rate or Float.NaN if unknown.
     */
    public float getHeartRate() {
        return Float.NaN;
    }


    /**
     * Whether the state will gather any acceleration information. This implementation returns false.
     */
    public boolean hasAcceleration() {
        return false;
    }

    /**
     * Get the x, y and z components of the acceleration in g.
     * @return array of acceleration or of Float.NaN if unknown
     */
    public float[] getAcceleration() {
        return new float[] {Float.NaN, Float.NaN, Float.NaN};
    }

    /**
     * Get the magnitude of the acceleration in g, computed from {@link #getAcceleration()}.
     * @return acceleration or Float.NaN if unknown.
     */
    public float getAccelerationMagnitude() {
        float[] acceleration = getAcceleration();
        return (float) Math.sqrt(
                acceleration[0] * acceleration[0]
                + acceleration[1] * acceleration[1]
                + acceleration[2] * acceleration[2]);
    }
}
