package org.radarcns.empaticaE4;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;

/**
 * Created by joris on 27/10/2016.
 */

public class E4DeviceStatus implements DeviceState {
    public static final Parcelable.Creator<E4DeviceStatus> CREATOR = new Parcelable.Creator<E4DeviceStatus>() {
        public E4DeviceStatus createFromParcel(Parcel in) {
            E4DeviceStatus result = new E4DeviceStatus();
            result.status = DeviceStatusListener.Status.values()[in.readInt()];
            result.acceleration[0] = in.readFloat();
            result.acceleration[1] = in.readFloat();
            result.acceleration[2] = in.readFloat();
            result.batteryLevel = in.readFloat();
            result.bloodVolumePulse = in.readFloat();
            result.electroDermalActivity = in.readFloat();
            result.interBeatInterval = in.readFloat();
            result.temperature = in.readFloat();
            return result;
        }

        public E4DeviceStatus[] newArray(int size) {
            return new E4DeviceStatus[size];
        }
    };

    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private float bloodVolumePulse = Float.NaN;
    private float electroDermalActivity = Float.NaN;
    private float interBeatInterval = Float.NaN;
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
        dest.writeFloat(this.bloodVolumePulse);
        dest.writeFloat(this.electroDermalActivity);
        dest.writeFloat(this.interBeatInterval);
        dest.writeFloat(this.temperature);
    }

    public float[] getAcceleration() {
        return acceleration;
    }

    public synchronized void setAcceleration(float x, float y, float z) {
        this.acceleration[0] = x;
        this.acceleration[1] = y;
        this.acceleration[2] = z;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public synchronized void setBatteryLevel(float batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public float getBloodVolumePulse() {
        return bloodVolumePulse;
    }

    public synchronized void setBloodVolumePulse(float bloodVolumePulse) {
        this.bloodVolumePulse = bloodVolumePulse;
    }

    public float getElectroDermalActivity() {
        return electroDermalActivity;
    }

    public synchronized void setElectroDermalActivity(float electroDermalActivity) {
        this.electroDermalActivity = electroDermalActivity;
    }

    public float getInterBeatInterval() {
        return interBeatInterval;
    }

    public synchronized void setInterBeatInterval(float interBeatInterval) {
        this.interBeatInterval = interBeatInterval;
    }

    public float getTemperature() {
        return temperature;
    }

    public synchronized void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public DeviceStatusListener.Status getStatus() {
        return status;
    }

    public synchronized void setStatus(DeviceStatusListener.Status status) {
        this.status = status;
    }
}
