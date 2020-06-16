package org.radarbase.garmin.interfaces;

import com.garmin.device.realtime.RealTimeAccelerometer;
import com.garmin.device.realtime.RealTimeAscent;
import com.garmin.device.realtime.RealTimeCalories;
import com.garmin.device.realtime.RealTimeHeartRate;
import com.garmin.device.realtime.RealTimeHeartRateVariability;
import com.garmin.device.realtime.RealTimeIntensityMinutes;
import com.garmin.device.realtime.RealTimeRespiration;
import com.garmin.device.realtime.RealTimeSpo2;
import com.garmin.device.realtime.RealTimeSteps;
import com.garmin.device.realtime.RealTimeStress;
import com.garmin.health.Device;

public interface OnHealthDataReceieved {
  void stepsReceived(RealTimeSteps steps);
  void heartRateReceived(RealTimeHeartRate heartRateResult);
  void ascentDataReceived(RealTimeAscent ascentResult);
  void calorieRecieved(RealTimeCalories caloriesResult);
  void spo2Received(RealTimeSpo2 spo2Result);
  void stressReceived(RealTimeStress stressResult);
  void heartRateVariabilityReceived(RealTimeHeartRateVariability heartRateVariabilityResult);
  void intensityMinutesReceived(RealTimeIntensityMinutes intensityMinutesResult);
  void accelerometerReceived(RealTimeAccelerometer accelerometerResult);
  void respirationReceived(RealTimeRespiration respirationResult);
  void deviceInfoDetailsReceived(Device device);
}
