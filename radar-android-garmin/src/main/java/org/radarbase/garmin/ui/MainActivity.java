package org.radarbase.garmin.ui;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.garmin.device.realtime.RealTimeDataType;
import com.garmin.device.realtime.RealTimeResult;
import com.garmin.device.realtime.listeners.RealTimeDataListener;
import com.garmin.health.DeviceManager;
import com.garmin.health.DevicePairedStateListener;
import com.garmin.health.GarminHealthInitializationException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Executors;
import org.radarbase.garmin.R;
import org.radarbase.garmin.devices.PairedDevicesDialogFragment;


/**
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
 * Created by jacksoncol on 6/22/17.
 */

public class MainActivity extends BaseGarminHealthActivity
{
    private static final String TAG = "MainActivity";

    private static String BROADCAST_PERMISSION_SUFFIX =
            ".permission.RECEIVE_BROADCASTS";


    private static final String[] permissions = new String[] {
            permission.ACCESS_COARSE_LOCATION,
            permission.WRITE_EXTERNAL_STORAGE,
            permission.READ_PHONE_STATE,
            permission.READ_CONTACTS,
            permission.ANSWER_PHONE_CALLS,
            permission.CALL_PHONE,
            permission.MEDIA_CONTENT_CONTROL};

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if(savedInstanceState != null)
        {
            return;
        }

        // Check that we have location permissions, required for bluetooth pairing.
        if(!verifyPermissions())
        {
            requestPermissions(permissions, REQUEST_COARSE_LOCATION);
            requestPermissions(new String[] {getApplicationContext().getPackageName()+BROADCAST_PERMISSION_SUFFIX}, 1);
        }

        // Initialize the SDK \\
        try
        {
            String PACKAGE_NAME = getPackageName();
            Futures.addCallback(HealthSDKManager.initializeHealthSDK(this), new FutureCallback<Boolean>()
            {
                @Override
                public void onSuccess(@Nullable Boolean result) {
                    runOnUiThread(() -> connectedDevicesTransition());
                }

                @Override
                public void onFailure(@NonNull Throwable t)
                {
                    runOnUiThread(() ->
                    {
                        Toast.makeText(getApplicationContext(), R.string.initialization_failed, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Garmin Health initialization failed.", t);

                    });
                }
            }, Executors.newSingleThreadExecutor());
        }
        catch (GarminHealthInitializationException e)
        {
            Toast.makeText(getApplicationContext(), R.string.initialization_failed, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Garmin Health initialization failed.", e);

            finishAndRemoveTask();
        }

    }

    private static final int REQUEST_COARSE_LOCATION = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch(requestCode)
        {
            case REQUEST_COARSE_LOCATION:

                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // Verify that the location services are available.
                    if(!verifyLocationServices())
                    {
                        Toast.makeText(getApplicationContext(), R.string.loc_service_unavailable, Toast.LENGTH_LONG).show();

                        finishAndRemoveTask();
                    }
                }

                break;
        }
    }

    /**  
     * Checks if the location permissions are enabled or not.
     *
     * @return true if permissions are available.
     */
    private boolean verifyPermissions()
    {
        boolean buildCondition = Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
        boolean permissionsCondition = true;

        for(String permission : permissions)
        {
            permissionsCondition &= (checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        }

        permissionsCondition &= (checkCallingOrSelfPermission(getApplicationContext().getPackageName()+BROADCAST_PERMISSION_SUFFIX) == PackageManager.PERMISSION_GRANTED);

        return buildCondition || permissionsCondition;

    }

    /**
     * Checks if the location services are enabled or not.
     *
     * @return true if services are available.
     */
    private boolean verifyLocationServices()
    {
        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    /**
     * Transitions to the fragment, which displays the paired devices
     */
    private void  connectedDevicesTransition()
    {
        DeviceManager.getDeviceManager().addPairedStateListener(new SetupListener(getApplicationContext()));

        PairedDevicesDialogFragment pairedDevicesDialogFragment = new PairedDevicesDialogFragment();

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        transaction.replace(R.id.main_root, pairedDevicesDialogFragment, pairedDevicesDialogFragment.getTag()).commit();
    }



    public static class SetupListener implements DevicePairedStateListener, RealTimeDataListener
    {
        private final Context mAppContext;

        SetupListener(Context appContext)
        {
            this.mAppContext = appContext.getApplicationContext();
        }


        @Override
        public void onDevicePaired(@NonNull com.garmin.health.Device device) {

        }

        @Override
        public void onDeviceSetupComplete(@NonNull com.garmin.health.Device device) {

        }

        @Override
        public void onDeviceUnpaired(@NonNull String macAddress) {}

        @Override
        public void onServiceDisconnected() {

        }

        @Override
        public void onDataUpdate(@NonNull String s, @NonNull RealTimeDataType realTimeDataType,
            @NonNull RealTimeResult realTimeResult) {
            Toast.makeText(mAppContext,"Success",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }
}
