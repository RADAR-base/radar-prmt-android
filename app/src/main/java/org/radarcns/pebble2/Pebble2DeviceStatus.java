package org.radarcns.pebble2;

import android.os.Parcel;

import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;

import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;

import java.util.EnumMap;
import java.util.Map;

/**
 * The status on a single point in time of an Empatica E4 device.
 */
public class Pebble2DeviceStatus implements DeviceState {
    private DeviceStatusListener.Status status = DeviceStatusListener.Status.READY;
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private float bloodVolumePulse = Float.NaN;
    private float electroDermalActivity = Float.NaN;
    private float interBeatInterval = Float.NaN;
    private float temperature = Float.NaN;
    private final Map<EmpaSensorType, EmpaSensorStatus> sensorStatus = new EnumMap<>(EmpaSensorType.class);

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
        dest.writeInt(sensorStatus.size());
        for (Map.Entry<EmpaSensorType, EmpaSensorStatus> sensor : sensorStatus.entrySet()) {
            dest.writeInt(sensor.getKey().ordinal());
            dest.writeInt(sensor.getValue().ordinal());
        }
    }

    public static final Creator<Pebble2DeviceStatus> CREATOR = new Creator<Pebble2DeviceStatus>() {
        public Pebble2DeviceStatus createFromParcel(Parcel in) {
            Pebble2DeviceStatus result = new Pebble2DeviceStatus();
            result.status = DeviceStatusListener.Status.values()[in.readInt()];
            result.acceleration[0] = in.readFloat();
            result.acceleration[1] = in.readFloat();
            result.acceleration[2] = in.readFloat();
            result.batteryLevel = in.readFloat();
            result.bloodVolumePulse = in.readFloat();
            result.electroDermalActivity = in.readFloat();
            result.interBeatInterval = in.readFloat();
            result.temperature = in.readFloat();
            int numSensors = in.readInt();
            for (int i = 0; i < numSensors; i++) {
                result.sensorStatus.put(EmpaSensorType.values()[in.readInt()], EmpaSensorStatus.values()[in.readInt()]);
            }
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

    public Map<EmpaSensorType, EmpaSensorStatus> getSensorStatus() {
        return sensorStatus;
    }

    public synchronized void setSensorStatus(EmpaSensorType type, EmpaSensorStatus status) {
        sensorStatus.put(type, status);
    }
}
