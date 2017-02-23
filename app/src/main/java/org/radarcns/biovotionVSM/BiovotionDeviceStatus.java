package org.radarcns.biovotionVSM;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.BaseDeviceState;
import org.radarcns.util.DeviceStateCreator;

/**
 * The status on a single point in time of a Biovotion VSM device.
 */
public class BiovotionDeviceStatus extends BaseDeviceState {
    private Float batteryLevel = Float.NaN;
    private Float batteryChargeRate = Float.NaN;
    private Float batteryVoltage = Float.NaN;
    private Float batteryStatus = Float.NaN;

    private Float bloodPulseWaveValue = Float.NaN;
    private Float bloodPulseWaveQuality = Float.NaN;

    private Float spo2Value = Float.NaN;
    private Float spo2Quality = Float.NaN;

    private Float heartRateValue = Float.NaN;
    private Float heartRateQuality = Float.NaN;

    private Float temperature = Float.NaN;
    private Float temperatureObject = Float.NaN;
    private Float temperatureBaro = Float.NaN;

    public static final Parcelable.Creator<BiovotionDeviceStatus> CREATOR = new DeviceStateCreator<>(BiovotionDeviceStatus.class);

    @Override
    public synchronized void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(this.batteryLevel);
        dest.writeFloat(this.batteryChargeRate);
        dest.writeFloat(this.batteryVoltage);
        dest.writeFloat(this.batteryStatus);
        dest.writeFloat(this.bloodPulseWaveValue);
        dest.writeFloat(this.bloodPulseWaveQuality);
        dest.writeFloat(this.spo2Value);
        dest.writeFloat(this.spo2Quality);
        dest.writeFloat(this.heartRateValue);
        dest.writeFloat(this.heartRateQuality);
        dest.writeFloat(this.temperature);
        dest.writeFloat(this.temperatureObject);
        dest.writeFloat(this.temperatureBaro);
    }

    public void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        batteryLevel = in.readFloat();
        batteryChargeRate = in.readFloat();
        batteryVoltage = in.readFloat();
        batteryStatus = in.readFloat();
        bloodPulseWaveValue = in.readFloat();
        bloodPulseWaveQuality = in.readFloat();
        spo2Value = in.readFloat();
        spo2Quality = in.readFloat();
        heartRateValue = in.readFloat();
        heartRateQuality = in.readFloat();
        temperature = in.readFloat();
        temperatureObject = in.readFloat();
        temperatureBaro = in.readFloat();
    }

    public float getBatteryLevel() { return batteryLevel; }
    public float getBatteryChargeRate() { return batteryChargeRate; }
    public float getBatteryVoltage() { return batteryVoltage; }
    public float getBatteryStatus() { return batteryStatus; }
    public float getBloodPulseWaveValue() { return bloodPulseWaveValue; }
    public float getBloodPulseWaveQuality() { return bloodPulseWaveQuality; }
    public float getSpO2Value() { return spo2Value; }
    public float getSpO2Quality() { return spo2Quality; }
    public float getHeartRate() { return heartRateValue; }
    public float getHeartRateValue() { return heartRateValue; }
    public float getHeartRateQuality() { return heartRateQuality; }
    public float getTemperature() { return temperature; }
    public float getTemperatureObject() { return temperature; }
    public float getTemperatureBaro() { return temperature; }


    public void setBatteryLevel(float cap) { this.batteryLevel = cap / 100.0f; }
    public void setBatteryChargeRate(float rate) { this.batteryChargeRate = rate / 100.0f; }
    public void setBatteryVoltage(float volt) { this.batteryVoltage = volt / 10.0f; }
    public void setBatteryStatus(float stat) { this.batteryStatus = stat; }
    public void setBloodPulseWaveValue(float BPWvalue) { this.bloodPulseWaveValue = BPWvalue / 50.0f; }
    public void setBloodPulseWaveQuality(float BPWquality) { this.bloodPulseWaveQuality = BPWquality / 100.0f; }
    public void setSpO2Value(float spo2value) { this.spo2Value = spo2value / 100.0f; }
    public void setSpO2Quality(float spo2quality) { this.spo2Quality = spo2quality / 100.0f; }
    public void setHeartRateValue(float HRvalue) { this.heartRateValue = HRvalue; }
    public void setHeartRateQuality(float HRquality) { this.heartRateQuality = HRquality / 100.0f; }
    public void setTemperature(float temp) { this.temperature = temp / 100.0f; }
    public void setTemperatureObject(float temp) { this.temperatureObject = temp / 100.0f; }
    public void setTemperatureBaro(float temp) { this.temperatureBaro = temp / 100.0f; }
}
