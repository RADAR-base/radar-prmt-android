package org.radarcns;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.RadarServiceProvider;
import org.radarcns.data.TimedInt;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.util.Boast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.radarcns.RadarConfiguration.DEFAULT_GROUP_ID_KEY;
import static org.radarcns.RadarConfiguration.UI_REFRESH_RATE_KEY;
import static org.radarcns.android.DeviceService.DEVICE_CONNECT_FAILED;
import static org.radarcns.android.DeviceService.DEVICE_STATUS_NAME;

public class MainActivity extends AppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_PERMISSIONS = 2;

    private long uiRefreshRate;
    private String groupId;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private Runnable mUIScheduler;
    private MainActivityUIUpdater mUIUpdater;
    private boolean isForcedDisconnected;

    /** Defines callbacks for service binding, passed to bindService() */
    private final BroadcastReceiver bluetoothReceiver;
    private final BroadcastReceiver deviceFailedReceiver;

    /** Connections. **/
    private List<RadarServiceProvider> mConnections;

    /** Overview UI **/
    private List<Button> mDeviceInputButtons;
    private final List<String> mInputDeviceKeys;
    private final List<Collection<String>> deviceKeys;
    private Button mGroupIdInputButton;

    private final List<TimedInt> mTotalRecordsSent;
    private String latestTopicSent;
    private final TimedInt latestNumberOfRecordsSent;

    private View mFirebaseStatusIcon;
    private TextView mFirebaseMessage;

    private final SparseIntArray rowMap;

    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public RadarConfiguration radarConfiguration;

    private final Runnable bindServicesRunner;
    private ServerStatusListener.Status serverStatus;

    public MainActivity() {
        super();
        isForcedDisconnected = false;
        serverStatus = null;

        rowMap = new SparseIntArray(4);
        rowMap.put(R.id.row1, 0);
        rowMap.put(R.id.row2, 1);
        rowMap.put(R.id.row3, 2);
        rowMap.put(R.id.row4, 3);

        mTotalRecordsSent = new ArrayList<>(5);
        mInputDeviceKeys = new ArrayList<>(5);
        deviceKeys = new ArrayList<>(5);

        bindServicesRunner = new Runnable() {
            @Override
            public void run() {
                for (RadarServiceProvider provider : mConnections) {
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
                            Boast.makeText(MainActivity.this, "Cannot connect to device " + intent.getStringExtra(DEVICE_STATUS_NAME), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };
        latestNumberOfRecordsSent = new TimedInt();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
        initializeViews();
        initializeRemoteConfig();

        // Start the UI thread
        uiRefreshRate = radarConfiguration.getLong(UI_REFRESH_RATE_KEY);
        groupId = radarConfiguration.getString(DEFAULT_GROUP_ID_KEY);
        mGroupIdInputButton.setText(groupId);
        mUIUpdater = new MainActivityUIUpdater(this, radarConfiguration);
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

        mConnections = RadarServiceProvider.loadProviders(this, radarConfiguration);
        for (int i = 0; i < mConnections.size(); i++) {
            mTotalRecordsSent.add(new TimedInt());
            mInputDeviceKeys.add("");
            deviceKeys.add(Collections.<String>emptyList());
        }

        // Not needed in API level 22.
        // checkBluetoothPermissions();

        // Check availability of Google Play Services
        if ( GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS ) {
            mFirebaseStatusIcon.setBackgroundResource(R.drawable.status_disconnected);
            mFirebaseMessage.setText(R.string.playServicesUnavailable);
        }
    }

    private void initializeViews() {
        mDeviceInputButtons = Arrays.asList(
                (Button) findViewById(R.id.inputDeviceNameButtonRow1),
                (Button) findViewById(R.id.inputDeviceNameButtonRow2),
                (Button) findViewById(R.id.inputDeviceNameButtonRow3),
                (Button) findViewById(R.id.inputDeviceNameButtonRow4)
        );

        mGroupIdInputButton = (Button) findViewById(R.id.inputGroupId);

        // Firebase
        mFirebaseStatusIcon = findViewById(R.id.firebaseStatus);
        mFirebaseMessage = (TextView) findViewById( R.id.firebaseStatusMessage);
    }

    private void initializeRemoteConfig() {
        // TODO: disable developer mode in production
        radarConfiguration = RadarConfiguration.configure(true, R.xml.remote_config_defaults);
        radarConfiguration.onFetchComplete(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Once the config is successfully fetched it must be
                    // activated before newly fetched values are returned.
                    radarConfiguration.activateFetched();
                    for (RadarServiceProvider provider : mConnections) {
                        provider.updateConfiguration();
                    }
                    logger.info("Remote Config: Activate success.");
                    // Set global properties.
                    mFirebaseStatusIcon.setBackgroundResource(R.drawable.status_connected);
                    mFirebaseMessage.setText("Remote config fetched from the server ("
                            + timeFormat.format( System.currentTimeMillis() ) + ")");
                    configureAtBoot();
                } else {
                    Toast.makeText(MainActivity.this, "Remote Config: Fetch Failed",
                            Toast.LENGTH_SHORT).show();
                    logger.info("Remote Config: Fetch failed. Stacktrace: {}", task.getException());
                }
            }
        });
        configureAtBoot();
    }

    private void configureAtBoot() {
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
                for (RadarServiceProvider connection : mConnections) {
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
        for (int i = 0; i < mConnections.size(); i++) {
            disconnect(i);
        }
    }

    private void disconnect(int row) {
        DeviceServiceConnection connection = mConnections.get(row).getConnection();
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
        for (int i = 0; i < mConnections.size(); i++) {
            DeviceServiceConnection connection = mConnections.get(i).getConnection();
            if (!connection.hasService() || connection.isRecording()) {
                continue;
            }
            Set<String> acceptableIds;
            if (!mInputDeviceKeys.get(i).isEmpty()) {
                acceptableIds = new HashSet<>(deviceKeys.get(i));
            } else {
                acceptableIds = Collections.emptySet();
            }
            try {
                logger.info("Starting recording on connection {}", i);
                connection.startRecording(acceptableIds);
            } catch (RemoteException e) {
                logger.error("Failed to start recording for device {}", i, e);
            }
        }
    }

    public void serviceConnected(final DeviceServiceConnection<?> connection) {
        try {
            ServerStatusListener.Status status = connection.getServerStatus();
            logger.info("Initial server status: {}", status);
            updateServerStatus(connection, status);
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
                    int rowNumber = getRow(connection);
                    RadarServiceProvider provider = mConnections.get(rowNumber);
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
//                        statusLabel.setText("CONNECTING");
                        logger.info( "Device name is {} while connecting.", connection.getDeviceName() );
                        int rowNumber = getRow(connection);
                        // Reject if device name inputted does not equal device nameA
                        if (!connection.isAllowedDevice(deviceKeys.get(rowNumber))) {
                            logger.info("Device name '{}' is not in the list of keys '{}'", connection.getDeviceName(), deviceKeys.get(rowNumber));
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

    private void checkBluetoothPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN};

        boolean waitingForPermission = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                waitingForPermission = true;
                break;
            }
        }
        if (waitingForPermission) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_ENABLE_PERMISSIONS);
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

    public void reconnectDevice(View v) {
        try {
            int rowIndex = getRowIndexFromView(v);
            // will restart scanning after disconnect
            disconnect(rowIndex);
        } catch (IndexOutOfBoundsException iobe) {
            Boast.makeText(this, "Could not restart scanning, there is no valid row index associated with this button.", Toast.LENGTH_LONG).show();
            logger.warn(iobe.getMessage());
        }
    }

    public void showDetails(final View v) {
        final int row;
        try {
            row = getRowIndexFromView(v);
        } catch (IndexOutOfBoundsException iobe) {
            logger.warn(iobe.getMessage());
            return;
        }

        final RadarServiceProvider provider = mConnections.get(row);
        if (provider.hasDetailView()) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mUIUpdater.update();
                        RadarServiceProvider provider = mConnections.get(row);
                        if (provider.hasDetailView()) {
                            provider.showDetailView();
                        }
                    } catch (RemoteException e) {
                        logger.warn("Failed to update view with device data");
                    }
                }
            });
        }

        if (radarConfiguration.isInDevelopmentMode()) {
            radarConfiguration.fetch();
        }
    }

    private int getRowIndexFromView(View v) throws IndexOutOfBoundsException {
        // Assume all elements are direct descendants from the TableRow
        int rowId = ((View) v.getParent()).getId();
        int ret = rowMap.get(rowId, -1);
        if (ret == -1) {
            throw new IndexOutOfBoundsException("Could not find row index of the given view.");
        }
        return ret;
    }

    public void updateServerStatus(DeviceServiceConnection<?> connection,
                                   final ServerStatusListener.Status status) {
        this.serverStatus = status;
    }

    public void updateServerRecordsSent(DeviceServiceConnection<?> connection, String topic,
                                        int numberOfRecords) {
        int row = getRow(connection);
        if (numberOfRecords >= 0){
            mTotalRecordsSent.get(row).add(numberOfRecords);
        }
        latestTopicSent = topic;
        latestNumberOfRecordsSent.set(numberOfRecords);
    }

    public void dialogInputDeviceName(final View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Device Serial Number:");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Setup the row
        final int row;
        try {
            row = getRowIndexFromView(v);
        } catch (IndexOutOfBoundsException iobe) {
            Boast.makeText(this, "Could not set this device key, there is no valid row index "
                    + "associated with this button.", Toast.LENGTH_LONG).show();
            logger.warn(iobe.getMessage());
            return;
        }

        // Set up the buttons
        input.setText(mInputDeviceKeys.get(row));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Remember previous value
                String oldValue = mInputDeviceKeys.get(row);
                String newValue = input.getText().toString().trim();

                // Set new value and process
                mInputDeviceKeys.set(row, newValue);
                Collection<String> allowed;
                String splitRegex = getString(R.string.deviceKeySplitRegex);
                allowed = new ArrayList<>(Arrays.asList(newValue.split(splitRegex)));
                Iterator<String> iter = allowed.iterator();
                // remove empty strings
                while (iter.hasNext()) {
                    if (iter.next().trim().isEmpty()) {
                        iter.remove();
                    }
                }
                deviceKeys.set(row, allowed);
                mDeviceInputButtons.get(row).setText(newValue);

                final DeviceServiceConnection connection = mConnections.get(row).getConnection();
                // Do NOT disconnect if input has not changed, is empty or equals the connected device.
                if (!newValue.equals(oldValue) &&
                    !newValue.isEmpty()        &&
                    !connection.isAllowedDevice(allowed))
                {
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
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void dialogInputGroupId(final View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Patient Identifier:");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        input.setText(groupId);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                groupId = input.getText().toString();
                mGroupIdInputButton.setText(groupId);

                // Set group/user id for each active connection
                try {
                    for (RadarServiceProvider provider : mConnections) {
                        DeviceServiceConnection connection = provider.getConnection();
                        if (connection.hasService()) {
                            connection.getDeviceData().getId().setUserId(groupId);
                        }
                    }
                } catch (RemoteException re) {
                    Boast.makeText(MainActivity.this, "Could not set the patient id", Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private int getRow(DeviceServiceConnection<?> connection) {
        for (int i = 0; i < mConnections.size(); i++) {
            String className = mConnections.get(i).getServiceClass().getName();
            if (className.equals(connection.getServiceClassName())) {
                return i;
            }
        }
        throw new IllegalArgumentException("DeviceServiceConnection "
                + connection.getServiceClassName() + " not set");
    }

    public ServerStatusListener.Status getServerStatus() {
        return serverStatus;
    }

    public TimedInt getTopicsSent(int row) {
        return mTotalRecordsSent.get(row);
    }

    public String getLatestTopicSent() {
        return latestTopicSent;
    }

    public TimedInt getLatestNumberOfRecordsSent() {
        return latestNumberOfRecordsSent;
    }

    public DeviceServiceConnection<?> getConnection(int i) {
        return mConnections.get(i).getConnection();
    }
}