package org.radarcns.phoneSensors;

import android.os.Parcel;

import org.radarcns.android.DeviceState;

/**
 * The status on a single point in time
 */
public class PhoneSensorsDeviceStatus extends DeviceState {
    private float[] acceleration = {Float.NaN, Float.NaN, Float.NaN};
    private float batteryLevel = Float.NaN;
    private float light = Float.NaN;
    //private String audioB64 = "";
    private int isRecordingAudio = 0;

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
        dest.writeFloat(this.light);
        //dest.writeString(this.audioB64);
        dest.writeInt(this.isRecordingAudio);
    }

    protected void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        acceleration[0] = in.readFloat();
        acceleration[1] = in.readFloat();
        acceleration[2] = in.readFloat();
        batteryLevel = in.readFloat();
        light = in.readFloat();
        isRecordingAudio = in.readInt();
        //audioB64 = in.readString();
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

    public float getLight() {
        return light;
    }

    public void setLight(float light) {
        this.light = light;
    }

    //public String getAudio(){ return audioB64;}
    public int getIsRecordingAudio(){return isRecordingAudio;}

    //public synchronized void setAudio(String audio){this.audioB64 = audio;}
    public synchronized void setIsRecordingAudio(int isRecordingAudio) {
        this.isRecordingAudio = isRecordingAudio;
    }
}
