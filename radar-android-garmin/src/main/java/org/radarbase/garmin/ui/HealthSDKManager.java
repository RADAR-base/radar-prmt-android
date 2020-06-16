package org.radarbase.garmin.ui;

import android.content.Context;

import com.garmin.health.AbstractGarminHealth;
import com.garmin.health.GarminHealth;
import com.garmin.health.GarminHealthInitializationException;
import com.google.common.util.concurrent.ListenableFuture;

import org.radarbase.garmin.R;


public class HealthSDKManager {
    /**
     * Initializes the health SDK for streaming
     * License should be acquired by contacting Garmin. Each license has restriction on the type of data that can be accessed.
     * @param context
     * @throws GarminHealthInitializationException
     */
    public static ListenableFuture<Boolean> initializeHealthSDK(Context context) throws GarminHealthInitializationException
    {
        if(!GarminHealth.isInitialized())
        {
            GarminHealth.setLoggingLevel(AbstractGarminHealth.LoggingLevel.VERBOSE);
        }
        String packageName = context.getPackageName();
        return GarminHealth.initialize(context, context.getString(R.string.companion_license));
    }
}
