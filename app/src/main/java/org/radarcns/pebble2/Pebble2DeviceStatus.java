package org.radarcns.pebble2;

import android.os.Parcel;

import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;

import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.util.Serialization;

import java.util.EnumMap;
import java.util.Map;

/**
 * The status on a single point in time of an Empatica E4 device.
 */
public class Pebble2DeviceStatus implements DeviceState {
    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;
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

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status.ordinal());
        dest.writeFloat(this.acceleration[0]);
        dest.writeFloat(this.acceleration[1]);
        dest.writeFloat(this.acceleration[2]);
        dest.writeFloat(this.batteryLevel);
        dest.writeByte(Serialization.booleanToByte(batteryIsCharging));
        dest.writeByte(Serialization.booleanToByte(batteryIsPlugged));
        dest.writeFloat(this.heartRate);
        dest.writeFloat(this.heartRateFiltered);
    }

    public static final Creator<Pebble2DeviceStatus> CREATOR = new Creator<Pebble2DeviceStatus>() {
        public Pebble2DeviceStatus createFromParcel(Parcel in) {
            Pebble2DeviceStatus result = new Pebble2DeviceStatus();
            result.status = DeviceStatusListener.Status.values()[in.readInt()];
            result.acceleration[0] = in.readFloat();
            result.acceleration[1] = in.readFloat();
            result.acceleration[2] = in.readFloat();
            result.batteryLevel = in.readFloat();
            result.batteryIsCharging = Serialization.byteToBoolean(in.readByte());
            result.batteryIsPlugged = Serialization.byteToBoolean(in.readByte());
            result.heartRate = in.readFloat();
            result.heartRateFiltered = in.readFloat();
            return result;
        }

        public Pebble2DeviceStatus[] newArray(int size) {
            return new Pebble2DeviceStatus[size];
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

    @Override
    public float getTemperature() {
        return Float.NaN;
    }

    public synchronized void setBatteryLevel(float batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    @Override
    public DeviceStatusListener.Status getStatus() {
        return status;
    }

    public void setStatus(DeviceStatusListener.Status status) {
        this.status = status;
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
}
