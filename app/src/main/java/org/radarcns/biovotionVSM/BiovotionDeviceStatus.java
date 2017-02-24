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

    private Float hrvValue = Float.NaN;
    private Float hrvQuality = Float.NaN;

    private Float rrValue = Float.NaN;
    private Float rrQuality = Float.NaN;

    private Float energyValue = Float.NaN;
    private Float energyQuality = Float.NaN;

    private Float temperature = Float.NaN;
    private Float temperatureObject = Float.NaN;
    private Float temperatureBaro = Float.NaN;

    private Float gsrAmplitude = Float.NaN;
    private Float gsrPhase = Float.NaN;

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
        dest.writeFloat(this.hrvValue);
        dest.writeFloat(this.hrvQuality);
        dest.writeFloat(this.rrValue);
        dest.writeFloat(this.rrQuality);
        dest.writeFloat(this.energyValue);
        dest.writeFloat(this.energyQuality);
        dest.writeFloat(this.temperature);
        dest.writeFloat(this.temperatureObject);
        dest.writeFloat(this.temperatureBaro);
        dest.writeFloat(this.gsrAmplitude);
        dest.writeFloat(this.gsrPhase);
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
        hrvValue = in.readFloat();
        hrvQuality = in.readFloat();
        rrValue = in.readFloat();
        rrQuality = in.readFloat();
        energyValue = in.readFloat();
        energyQuality = in.readFloat();
        temperature = in.readFloat();
        temperatureObject = in.readFloat();
        temperatureBaro = in.readFloat();
        gsrAmplitude = in.readFloat();
        gsrPhase = in.readFloat();
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
    public float getHrvValue() { return hrvValue; }
    public float getHrvQuality() { return hrvQuality; }
    public float getRrValue() { return rrValue; }
    public float getRrQuality() { return rrQuality; }
    public float getEnergyValue() { return energyValue; }
    public float getEnergyQuality() { return energyQuality; }
    public float getTemperature() { return temperature; }
    public float getTemperatureObject() { return temperature; }
    public float getTemperatureBaro() { return temperature; }
    public float getGsrAmplitude() { return gsrAmplitude; }
    public float getGsrPhase() { return gsrPhase; }


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
    public void setHrvValue(float HRVvalue) { this.hrvValue = HRVvalue; }
    public void setHrvQuality(float HRVquality) { this.hrvQuality = HRVquality / 100.0f; }
    public void setRrValue(float RRvalue) { this.rrValue = RRvalue; }
    public void setRrQuality(float RRquality) { this.rrQuality = RRquality / 100.0f; }
    public void setEnergyValue(float NRGvalue) { this.energyValue = NRGvalue * 2.0f; }
    public void setEnergyQuality(float NRGquality) { this.energyQuality = NRGquality / 100.0f; }
    public void setTemperature(float temp) { this.temperature = temp / 100.0f; }
    public void setTemperatureObject(float temp) { this.temperatureObject = temp / 100.0f; }
    public void setTemperatureBaro(float temp) { this.temperatureBaro = temp / 100.0f; }
    public void setGsrAmplitude(float GSRamp) { this.gsrAmplitude = GSRamp / 3000.0f; }
    public void setGsrPhase(float GSRphase) { this.gsrPhase = GSRphase; }
}
