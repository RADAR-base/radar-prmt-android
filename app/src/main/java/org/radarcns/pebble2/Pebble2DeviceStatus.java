package org.radarcns.pebble2;

import android.os.Parcel;

import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Serialization;

import java.util.Arrays;

/**
 * The status on a single point in time of an Empatica E4 device.
 */
public class Pebble2DeviceStatus extends DeviceState {
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private Boolean batteryIsCharging = null;
    private Boolean batteryIsPlugged = null;
    private float heartRate = Float.NaN;
    private float heartRateFiltered = Float.NaN;

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Pebble2DeviceStatus> CREATOR = new Creator<Pebble2DeviceStatus>() {
        public Pebble2DeviceStatus createFromParcel(Parcel in) {
            Pebble2DeviceStatus result = new Pebble2DeviceStatus();
            result.updateFromParcel(in);
            return result;
        }

        public Pebble2DeviceStatus[] newArray(int size) {
            return new Pebble2DeviceStatus[size];
        }
    };

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(this.acceleration[0]);
        dest.writeFloat(this.acceleration[1]);
        dest.writeFloat(this.acceleration[2]);
        dest.writeFloat(this.batteryLevel);
        dest.writeByte(Serialization.booleanToByte(batteryIsCharging));
        dest.writeByte(Serialization.booleanToByte(batteryIsPlugged));
        dest.writeFloat(this.heartRate);
        dest.writeFloat(this.heartRateFiltered);
    }

    protected void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        acceleration[0] = in.readFloat();
        acceleration[1] = in.readFloat();
        acceleration[2] = in.readFloat();
        batteryLevel = in.readFloat();
        batteryIsCharging = Serialization.byteToBoolean(in.readByte());
        batteryIsPlugged = Serialization.byteToBoolean(in.readByte());
        heartRate = in.readFloat();
        heartRateFiltered = in.readFloat();
    }

    @Override
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

    public Boolean getBatteryIsCharging() {
        return batteryIsCharging;
    }

    public void setBatteryIsCharging(Boolean batteryIsCharging) {
        this.batteryIsCharging = batteryIsCharging;
    }

    public Boolean getBatteryIsPlugged() {
        return batteryIsPlugged;
    }

    public void setBatteryIsPlugged(Boolean batteryIsPlugged) {
        this.batteryIsPlugged = batteryIsPlugged;
    }

    @Override
    public float getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(float heartRate) {
        this.heartRate = heartRate;
    }

    public float getHeartRateFiltered() {
        return heartRateFiltered;
    }

    public void setHeartRateFiltered(float heartRateFiltered) {
        this.heartRateFiltered = heartRateFiltered;
    }

    public String toString() {
        return "{status: " + getStatus() + ", acceleration: " + Arrays.toString(acceleration) +
                ", batteryLevel: " + batteryLevel + ", heartRate: " + heartRate +
                ", heartRateFiltered: " + heartRateFiltered + "}";
    }
}
