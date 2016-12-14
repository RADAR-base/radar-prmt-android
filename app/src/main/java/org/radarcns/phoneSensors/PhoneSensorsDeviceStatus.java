package org.radarcns.phoneSensors;

import android.os.Parcel;

import org.radarcns.android.DeviceState;

/**
 * The status on a single point in time
 */
public class PhoneSensorsDeviceStatus extends DeviceState {
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private float temperature = Float.NaN;
    private float light = Float.NaN;

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PhoneSensorsDeviceStatus> CREATOR = new Creator<PhoneSensorsDeviceStatus>() {
        public PhoneSensorsDeviceStatus createFromParcel(Parcel in) {
            PhoneSensorsDeviceStatus result = new PhoneSensorsDeviceStatus();
            result.updateFromParcel(in);
            return result;
        }

        public PhoneSensorsDeviceStatus[] newArray(int size) {
            return new PhoneSensorsDeviceStatus[size];
        }
    };

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(this.acceleration[0]);
        dest.writeFloat(this.acceleration[1]);
        dest.writeFloat(this.acceleration[2]);
        dest.writeFloat(this.batteryLevel);
        dest.writeFloat(this.temperature);
        dest.writeFloat(this.light);
    }

    protected void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        acceleration[0] = in.readFloat();
        acceleration[1] = in.readFloat();
        acceleration[2] = in.readFloat();
        batteryLevel = in.readFloat();
        temperature = in.readFloat();
        light = in.readFloat();
    }

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
        return light; // TODO: DEBUG setting: report light as HR in ui
    }

    @Override
    public float getTemperature() {
        return temperature;
    }

    public synchronized void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getLight() {
        return light;
    }

    public void setLight(float light) {
        this.light = light;
    }

}
