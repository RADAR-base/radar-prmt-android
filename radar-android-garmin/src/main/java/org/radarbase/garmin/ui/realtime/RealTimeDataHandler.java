/*
 * Copyright (c) 2017 Garmin International. All Rights Reserved.
 * <p></p>
 * This software is the confidential and proprietary information of
 * Garmin International.
 * You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement
 * you entered into with Garmin International.
 * <p></p>
 * Garmin International MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. Garmin International SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * <p></p>
 * Created by johnsongar on 3/9/2017.
 */
package org.radarbase.garmin.ui.realtime;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import com.garmin.device.realtime.RealTimeDataType;
import com.garmin.device.realtime.RealTimeResult;
import com.garmin.device.realtime.listeners.RealTimeDataListener;
import java.util.HashMap;
import com.garmin.health.Device;
import org.radarbase.garmin.interfaces.OnHealthDataReceieved;

public class RealTimeDataHandler implements RealTimeDataListener {

    private static final String TAG = RealTimeDataHandler.class.getSimpleName();

    private static RealTimeDataHandler sInstance;

    private static Device device;


    private final HashMap<String, HashMap<RealTimeDataType, RealTimeResult>> mLatestData;


    public synchronized static RealTimeDataHandler getInstance() {
        if (sInstance == null) {
            sInstance = new RealTimeDataHandler();
        }
        return sInstance;
    }

    private static OnHealthDataReceieved healthDataRecieved;

    private RealTimeDataHandler() {
        mLatestData = new HashMap<>();
    }

    public HashMap<RealTimeDataType, RealTimeResult> getLatestData(String deviceAddress) {
        return mLatestData.get(deviceAddress);
    }

    public void registerListenerForHealthData(OnHealthDataReceieved healthDataRecieved) {
        RealTimeDataHandler.healthDataRecieved = healthDataRecieved;
    }

    @SuppressLint("DefaultLocale")
    public static void logRealTimeData(String tag, String macAddress, RealTimeDataType dataType, RealTimeResult result) {
        //Log out the main value for each data type
        if(healthDataRecieved!=null)
        {
            switch (dataType) {
                case STEPS:
                    healthDataRecieved.stepsReceived(result.getSteps());
                    break;
                case HEART_RATE_VARIABILITY:
                    healthDataRecieved.heartRateVariabilityReceived(result.getHeartRateVariability());
                    break;
                case CALORIES:
                    healthDataRecieved.calorieRecieved(result.getCalories());
                    break;
                case ASCENT:
                    healthDataRecieved.ascentDataReceived(result.getAscent());
                    break;
                case INTENSITY_MINUTES:
                    healthDataRecieved.intensityMinutesReceived(result.getIntensityMinutes());
                    break;
                case HEART_RATE:
                    healthDataRecieved.heartRateReceived(result.getHeartRate());
                    break;
                case STRESS:
                    healthDataRecieved.stressReceived(result.getStress());
                    break;
                case ACCELEROMETER:
                    healthDataRecieved.accelerometerReceived(result.getAccelerometer());
                    break;
                case SPO2:
                    healthDataRecieved.spo2Received(result.getSpo2());
                    break;
                case RESPIRATION:
                    healthDataRecieved.respirationReceived(result.getRespiration());
                    break;
            }
        }

    }

    public void setDevice(Device device) {
        RealTimeDataHandler.device = device;
        if(healthDataRecieved!=null)
            healthDataRecieved.deviceInfoDetailsReceived(device);
    }

    @Override
    public void onDataUpdate(@NonNull String macAddress, @NonNull RealTimeDataType dataType, @NonNull RealTimeResult result) {
        logRealTimeData(TAG, macAddress, dataType, result);

        //Cache last received data of each type
        //Used to display values if device loses connection
        HashMap<RealTimeDataType, RealTimeResult> latestData = mLatestData.get(macAddress);
        if (latestData == null) {
            latestData = new HashMap<>();
            mLatestData.put(macAddress, latestData);
        }
        latestData.put(dataType, result);
    }
}
