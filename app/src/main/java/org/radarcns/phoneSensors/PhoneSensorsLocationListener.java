package org.radarcns.phoneSensors;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhoneSensorsLocationListener implements LocationListener {
    private final static Logger logger = LoggerFactory.getLogger(PhoneSensorsDeviceManager.class);

    @Override
    public void onLocationChanged(Location location) {
        // Called when a new location is found by the network location provider.
        // 1 - Determine whether location is better than previous location
        // 2 - Save if it is
        // 3 - At end, send data to PhoneSensorsDeviceManager
//        makeUseOfNewLocation(location);
        logger.info("Location: location changed {} {}", location.getLatitude(), location.getLongitude());
        logger.info("Location: {}", location.toString());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        logger.info("Location: status changed");
    }

    @Override
    public void onProviderEnabled(String provider) {
        logger.info("Location: enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        logger.info("Location: disabled");
    }
}