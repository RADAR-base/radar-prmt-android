package org.radarcns.biovotionVSM;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.BaseDeviceState;
import org.radarcns.util.DeviceStateCreator;

/**
 * The status on a single point in time of a Biovotion VSM device.
 */
public class BiovotionDeviceStatus extends BaseDeviceState {
    private Integer batteryCapacity = null;
    private Integer batteryChargeRate = null;
    private Float batteryVoltage = Float.NaN;
    private Integer batteryStatus = null;

    private Float bloodPulseWaveValue = Float.NaN;
    private Integer bloodPulseWaveQuality = null;

    private Integer heartRateValue = null;
    private Integer heartRateQuality = null;

    public static final Parcelable.Creator<BiovotionDeviceStatus> CREATOR = new DeviceStateCreator<>(BiovotionDeviceStatus.class);

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(this.batteryCapacity);
        dest.writeInt(this.batteryChargeRate);
        dest.writeFloat(this.batteryVoltage);
        dest.writeInt(this.batteryStatus);
        dest.writeFloat(this.bloodPulseWaveValue);
        dest.writeInt(this.bloodPulseWaveQuality);
        dest.writeInt(this.heartRateValue);
        dest.writeInt(this.heartRateQuality);
    }

    public void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        batteryCapacity = in.readInt();
        batteryChargeRate = in.readInt();
        batteryVoltage = in.readFloat();
        batteryStatus = in.readInt();
        bloodPulseWaveValue = in.readFloat();
        bloodPulseWaveQuality = in.readInt();
        heartRateValue = in.readInt();
        heartRateQuality = in.readInt();
    }

    public int getBatteryCapacity() { return batteryCapacity; }
    public int getBatteryChargeRate() { return batteryChargeRate; }
    public float getBatteryVoltage() { return batteryVoltage; }
    public int getBatteryStatus() { return batteryStatus; }
    public float getBloodPulseWaveValue() { return bloodPulseWaveValue; }
    public int getBloodPulseWaveQuality() { return bloodPulseWaveQuality; }
    public int getHeartRateValue() { return heartRateValue; }
    public int getHeartRateQuality() { return heartRateQuality; }

    public void setBatteryCapacity(int cap) { this.batteryCapacity = cap; }
    public void setBatteryChargeRate(int rate) { this.batteryChargeRate = rate; }
    public void setBatteryVoltage(float volt) { this.batteryVoltage = volt; }
    public void setBatteryStatus(int stat) { this.batteryStatus = stat; }
    public void setBloodPulseWaveValue(float BPWvalue) { this.bloodPulseWaveValue = BPWvalue; }
    public void setBloodPulseWaveQuality(int BPWquality) { this.bloodPulseWaveQuality = BPWquality; }
    public void setHeartRateValue(int HRvalue) { this.heartRateValue = HRvalue; }
    public void setHeartRateQuality(int HRquality) { this.heartRateQuality = HRquality; }
}
