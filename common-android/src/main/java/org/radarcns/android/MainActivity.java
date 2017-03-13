package org.radarcns.android;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.radarcns.android.device.DeviceServiceConnection;
import org.radarcns.android.device.DeviceServiceProvider;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.kafka.ServerStatusListener;
import org.radarcns.android.util.Boast;
import org.radarcns.data.TimedInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.INTERNET;
import static org.radarcns.android.device.DeviceService.DEVICE_CONNECT_FAILED;
import static org.radarcns.android.device.DeviceService.DEVICE_STATUS_NAME;

public abstract class MainActivity extends AppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_PERMISSIONS = 2;
    private final Map<DeviceServiceConnection, Set<String>> mInputDeviceKeys;

    private long uiRefreshRate;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private Runnable mUIScheduler;
    private MainActivityView mUIUpdater;
    private boolean isForcedDisconnected;

    /** Defines callbacks for service binding, passed to bindService() */
    private final BroadcastReceiver bluetoothReceiver;
    private final BroadcastReceiver deviceFailedReceiver;

    /** Connections. **/
    private List<DeviceServiceProvider> mConnections;

    private final Map<DeviceServiceConnection, TimedInt> mTotalRecordsSent;
    private String latestTopicSent;
    private final TimedInt latestNumberOfRecordsSent;

    private RadarConfiguration radarConfiguration;

    private final Runnable bindServicesRunner;
    private ServerStatusListener.Status serverStatus;

    public MainActivity() {
        super();
        isForcedDisconnected = false;
        serverStatus = null;

        mTotalRecordsSent = new HashMap<>();
        mInputDeviceKeys = new HashMap<>();

        bindServicesRunner = new Runnable() {
            @Override
            public void run() {
                for (DeviceServiceProvider provider : mConnections) {
                    if (!provider.isBound()) {
                        provider.bind();
                    }
                }
            }

        };

        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    logger.info("Bluetooth state {}", state);
                    // Upon state change, restart ui handler and restart Scanning.
                    if (state == BluetoothAdapter.STATE_ON) {
                        logger.info("Bluetooth is on");
                        startScanning();
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        logger.warn("Bluetooth is off");
                        startScanning();
                    }
                }
            }
        };

        deviceFailedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                if (intent.getAction().equals(DEVICE_CONNECT_FAILED)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Boast.makeText(MainActivity.this, "Cannot connect to device "
                                    + intent.getStringExtra(DEVICE_STATUS_NAME),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };
        latestNumberOfRecordsSent = new TimedInt();
    }

    @Override
    public boolean supportRequestWindowFeature(int featureId) {
        return super.supportRequestWindowFeature(featureId);
    }

    protected abstract RadarConfiguration createConfiguration();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: disable developer mode in production
        radarConfiguration = createConfiguration();
        configureRunAtBoot();

        mConnections = DeviceServiceProvider.loadProviders(this, radarConfiguration);
        for (DeviceServiceProvider provider : mConnections) {
            DeviceServiceConnection connection = provider.getConnection();
            mTotalRecordsSent.put(connection, new TimedInt());
            mInputDeviceKeys.put(connection, Collections.<String>emptySet());
        }

        radarConfiguration.onFetchComplete(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Once the config is successfully fetched it must be
                    // activated before newly fetched values are returned.
                    radarConfiguration.activateFetched();
                    for (DeviceServiceProvider provider : mConnections) {
                        provider.updateConfiguration();
                    }
                    logger.info("Remote Config: Activate success.");
                    // Set global properties.
                    configureRunAtBoot();
                } else {
                    Boast.makeText(MainActivity.this, "Remote Config: Fetch Failed",
                            Toast.LENGTH_SHORT).show();
                    logger.info("Remote Config: Fetch failed. Stacktrace: {}", task.getException());
                }
            }
        });

        // Start the UI thread
        uiRefreshRate = radarConfiguration.getLong(RadarConfiguration.UI_REFRESH_RATE_KEY);
        mUIUpdater = createView();
        mUIScheduler = new Runnable() {
            @Override
            public void run() {
                try {
                    // Update all rows in the UI with the data from the connections
                    mUIUpdater.update();
                } catch (RemoteException e) {
                    logger.warn("Failed to update device data", e);
                } finally {
                    getHandler().postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

        if (getApplicationInfo().targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1) {
            checkPermissions();
        }
    }

    protected abstract MainActivityView createView();

    private void configureRunAtBoot() {
        ComponentName receiver = new ComponentName(
                getApplicationContext(), MainActivityStarter.class);
        PackageManager pm = getApplicationContext().getPackageManager();

        boolean startAtBoot = radarConfiguration.getBoolean(RadarConfiguration.START_AT_BOOT, false);
        boolean isStartedAtBoot = pm.getComponentEnabledSetting(receiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        if (startAtBoot && !isStartedAtBoot) {
            logger.info("From now on, this application will start at boot");
            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } else if (!startAtBoot && isStartedAtBoot) {
            logger.info("Not starting application at boot anymore");
            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    protected void onResume() {
        logger.info("mainActivity onResume");
        super.onResume();
        getHandler().post(bindServicesRunner);
        radarConfiguration.fetch();
        getHandler().post(mUIScheduler);
    }

    @Override
    protected void onPause() {
        logger.info("mainActivity onPause");
        super.onPause();
        getHandler().removeCallbacks(mUIScheduler);
    }

    @Override
    protected void onStart() {
        logger.info("mainActivity onStart");
        super.onStart();
        registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(deviceFailedReceiver, new IntentFilter(DEVICE_CONNECT_FAILED));

        mHandlerThread = new HandlerThread("E4Service connection", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Handler localHandler = new Handler(mHandlerThread.getLooper());
        synchronized (this) {
            mHandler = localHandler;
        }
    }

    @Override
    protected void onStop() {
        logger.info("mainActivity onStop");
        super.onStop();
        unregisterReceiver(deviceFailedReceiver);
        unregisterReceiver(bluetoothReceiver);
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                for (DeviceServiceProvider connection : mConnections) {
                    if (connection.isBound()) {
                        connection.unbind();
                    }
                }
            }
        });
        synchronized (this) {
            mHandler = null;
        }
        mHandlerThread.quitSafely();
    }

    private synchronized Handler getHandler() {
        return mHandler;
    }

    private void disconnect() {
        for (DeviceServiceProvider provider : mConnections) {
            disconnect(provider.getConnection());
        }
    }

    public void disconnect(DeviceServiceConnection connection) {
        if (connection.isRecording()) {
            try {
                connection.stopRecording();
            } catch (RemoteException e) {
                // it cannot be reached so it already stopped recording
            }
        }
    }

    /**
     * If no E4Service is scanning, and ask one to start scanning.
     */
    private void startScanning() {
        if (isForcedDisconnected) {
            return;
        } else if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            enableBt();
            return;
        }
        for (DeviceServiceProvider provider : mConnections) {
            DeviceServiceConnection connection = provider.getConnection();
            if (!connection.hasService() || connection.isRecording()) {
                continue;
            }
            try {
                logger.info("Starting recording on connection {}", connection);
                connection.startRecording(mInputDeviceKeys.get(connection));
            } catch (RemoteException ex) {
                logger.error("Failed to start recording for device {}", connection, ex);
            }
        }
    }

    public void serviceConnected(final DeviceServiceConnection<?> connection) {
        try {
            ServerStatusListener.Status status = connection.getServerStatus();
            logger.info("Initial server status: {}", status);
            updateServerStatus(status);
        } catch (RemoteException e) {
            logger.warn("Failed to update UI server status");
        }
        startScanning();
    }

    public synchronized void serviceDisconnected(final DeviceServiceConnection<?> connection) {
        if (mHandler != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    DeviceServiceProvider provider = getConnectionProvider(connection);
                    logger.info("Rebinding {} after disconnect", provider);
                    if (provider.isBound()) {
                        provider.unbind();
                    }
                    provider.bind();
                }
            });
        }
    }

    public void deviceStatusUpdated(final DeviceServiceConnection connection, final DeviceStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Boast.makeText(MainActivity.this, status.toString(), Toast.LENGTH_SHORT).show();
                switch (status) {
                    case CONNECTED:
                        break;
                    case CONNECTING:
                        logger.info( "Device name is {} while connecting.", connection.getDeviceName() );
                        // Reject if device name inputted does not equal device nameA
                        if (!connection.isAllowedDevice(mInputDeviceKeys.get(connection))) {
                            logger.info("Device name '{}' is not in the list of keys '{}'", connection.getDeviceName(), mInputDeviceKeys.get(connection));
                            Boast.makeText(MainActivity.this, String.format("Device '%s' rejected", connection.getDeviceName()), Toast.LENGTH_LONG).show();
                            disconnect();
                        }
                        break;
                    case DISCONNECTED:
                        startScanning();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    void enableBt() {
        BluetoothAdapter btAdaptor = BluetoothAdapter.getDefaultAdapter();
        if (!btAdaptor.isEnabled() && btAdaptor.getState() != BluetoothAdapter.STATE_TURNING_ON) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(btIntent);
        }
    }

    protected List<String> getActivityPermissions() {
        return Arrays.asList(ACCESS_NETWORK_STATE, INTERNET);
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.addAll(getActivityPermissions());
        for (DeviceServiceProvider<?> provider : mConnections) {
            permissions.addAll(provider.needsPermissions());
        }

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_ENABLE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted.
                startScanning();
            } else {
                // User refused to grant permission.
                Boast.makeText(this, "Cannot connect to Empatica E4DeviceManager without location permissions", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void updateServerStatus(final ServerStatusListener.Status status) {
        this.serverStatus = status;
    }

    public void updateServerRecordsSent(DeviceServiceConnection<?> connection, String topic,
                                        int numberOfRecords) {
        if (numberOfRecords >= 0){
            mTotalRecordsSent.get(connection).add(numberOfRecords);
        }
        latestTopicSent = topic;
        latestNumberOfRecordsSent.set(numberOfRecords);
    }

    public void setAllowedDeviceIds(final DeviceServiceConnection connection, Set<String> allowedIds) {
        // Do NOT disconnect if input has not changed, is empty or equals the connected device.
        if (!connection.isAllowedDevice(allowedIds)) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (connection.isRecording()) {
                            connection.stopRecording();
                            // will restart recording once the status is set to disconnected.
                        }
                    } catch (RemoteException e) {
                        logger.error("Cannot restart scanning");
                    }
                }
            });
        }
    }

    private DeviceServiceProvider getConnectionProvider(DeviceServiceConnection<?> connection) {
        for (DeviceServiceProvider provider : mConnections) {
            if (provider.getConnection().equals(connection)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("DeviceServiceConnection "
                + connection.getServiceClassName() + " not set");
    }

    public ServerStatusListener.Status getServerStatus() {
        return serverStatus;
    }

    public TimedInt getTopicsSent(DeviceServiceConnection connection) {
        return mTotalRecordsSent.get(connection);
    }

    public String getLatestTopicSent() {
        return latestTopicSent;
    }

    public TimedInt getLatestNumberOfRecordsSent() {
        return latestNumberOfRecordsSent;
    }

    public List<DeviceServiceProvider> getConnections() {
        return Collections.unmodifiableList(mConnections);
    }

    public RadarConfiguration getRadarConfiguration() {
        return radarConfiguration;
    }
}