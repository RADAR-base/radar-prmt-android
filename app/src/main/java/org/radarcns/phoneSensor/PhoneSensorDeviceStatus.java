package org.radarcns.phoneSensor;

import android.os.Parcel;

import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;

/**
 * The status on a single point in time
 */
public class PhoneSensorDeviceStatus implements DeviceState {
    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private float temperature = Float.NaN;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status.ordinal());
        dest.writeFloat(this.acceleration[0]);
        dest.writeFloat(this.acceleration[1]);
        dest.writeFloat(this.acceleration[2]);
        dest.writeFloat(this.batteryLevel);
        dest.writeFloat(this.temperature);
    }

    public static final Creator<PhoneSensorDeviceStatus> CREATOR = new Creator<PhoneSensorDeviceStatus>() {
        public PhoneSensorDeviceStatus createFromParcel(Parcel in) {
            PhoneSensorDeviceStatus result = new PhoneSensorDeviceStatus();
            result.status = DeviceStatusListener.Status.values()[in.readInt()];
            result.acceleration[0] = in.readFloat();
            result.acceleration[1] = in.readFloat();
            result.acceleration[2] = in.readFloat();
            result.batteryLevel = in.readFloat();
            result.temperature = in.readFloat();
            return result;
        }

        public PhoneSensorDeviceStatus[] newArray(int size) {
            return new PhoneSensorDeviceStatus[size];
        }
    };

    public float[] getAcceleration() {
        return acceleration;
    }

    public synchronized void setAcceleration(float x, float y, float z) {
        this.acceleration[0] = x;
        this.acceleration[1] = y;
        this.acceleration[2] = z;
    }

    @Override
    public float getBatteryLevel() {
        return batteryLevel;
    }

    public synchronized void setBatteryLevel(float batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    @Override
    public float getHeartRate() {
        return Float.NaN;
    }

    @Override
    public float getTemperature() {
        return temperature;
    }

    public synchronized void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    @Override
    public DeviceStatusListener.Status getStatus() {
        return status;
    }

    public synchronized void setStatus(DeviceStatusListener.Status status) {
        this.status = status;
    }

}
