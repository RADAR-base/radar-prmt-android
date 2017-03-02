package org.radarcns;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
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
import org.radarcns.data.TimedInt;
import org.radarcns.empaticaE4.E4DeviceStatus;
import org.radarcns.empaticaE4.E4HeartbeatToast;
import org.radarcns.empaticaE4.E4Service;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.pebble2.Pebble2DeviceStatus;
import org.radarcns.pebble2.Pebble2HeartbeatToast;
import org.radarcns.pebble2.Pebble2Service;
import org.radarcns.phoneSensors.PhoneSensorsDeviceStatus;
import org.radarcns.phoneSensors.PhoneSensorsService;
import org.radarcns.util.Boast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import static org.radarcns.RadarConfiguration.CALL_SMS_LOG_UPDATE_RATE_KEY;
import static org.radarcns.RadarConfiguration.DEVICE_GROUP_ID_KEY;
import static org.radarcns.RadarConfiguration.EMPATICA_API_KEY;
import static org.radarcns.RadarConfiguration.KAFKA_CLEAN_RATE_KEY;
import static org.radarcns.RadarConfiguration.KAFKA_RECORDS_SEND_LIMIT_KEY;
import static org.radarcns.RadarConfiguration.KAFKA_REST_PROXY_URL_KEY;
import static org.radarcns.RadarConfiguration.KAFKA_UPLOAD_RATE_KEY;
import static org.radarcns.RadarConfiguration.LOCATION_GPS_UPDATE_RATE_KEY;
import static org.radarcns.RadarConfiguration.LOCATION_NETWORK_UPDATE_RATE_KEY;
import static org.radarcns.RadarConfiguration.SCHEMA_REGISTRY_URL_KEY;
import static org.radarcns.RadarConfiguration.SENDER_CONNECTION_TIMEOUT_KEY;
import static org.radarcns.RadarConfiguration.UI_REFRESH_RATE_KEY;
import static org.radarcns.android.DeviceService.DEVICE_CONNECT_FAILED;
import static org.radarcns.android.DeviceService.DEVICE_STATUS_NAME;

public class MainActivity extends AppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_PERMISSIONS = 2;

    private long uiRefreshRate;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private Runnable mUIScheduler;
    private MainActivityUIUpdater mUIUpdater;
    private boolean isForcedDisconnected;
    private final boolean[] mConnectionIsBound;

    /** Defines callbacks for service binding, passed to bindService() */
    private final DeviceServiceConnection<E4DeviceStatus> mE4Connection;
    private final DeviceServiceConnection<Pebble2DeviceStatus> pebble2Connection;
    private final DeviceServiceConnection<PhoneSensorsDeviceStatus> phoneConnection;
    private final BroadcastReceiver bluetoothReceiver;
    private final BroadcastReceiver deviceFailedReceiver;

    /** Connections. 0 = Empatica, 1 = Angel sensor, 2 = Pebble sensor **/
    private DeviceServiceConnection[] mConnections;

    /** Overview UI **/
    private Button[] mDeviceInputButtons;
    private final String[] mInputDeviceKeys = new String[4];

    private final TimedInt[] mTotalRecordsSent;
    private String latestTopicSent;
    private final TimedInt latestNumberOfRecordsSent;

    private View mFirebaseStatusIcon;
    private TextView mFirebaseMessage;

    private final SparseIntArray rowMap;

    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public RadarConfiguration radarConfiguration;

    private final Runnable bindServicesRunner;
    private ServerStatusListener.Status serverStatus;

    private void configureEmpatica(Bundle bundle) {
        configureServiceExtras(bundle);
        radarConfiguration.putExtras(bundle, EMPATICA_API_KEY);
    }

    private void configurePebble2(Bundle bundle) {
        configureServiceExtras(bundle);
    }

    private void configurePhoneSensors(Bundle bundle) {
        configureServiceExtras(bundle);
        radarConfiguration.putExtras(bundle, CALL_SMS_LOG_UPDATE_RATE_KEY,
                LOCATION_GPS_UPDATE_RATE_KEY, LOCATION_NETWORK_UPDATE_RATE_KEY);
    }

    private void configureServiceExtras(Bundle bundle) {
        // Add the default configuration parameters given to the service intents
        radarConfiguration.putExtras(bundle,
                KAFKA_REST_PROXY_URL_KEY, SCHEMA_REGISTRY_URL_KEY, DEVICE_GROUP_ID_KEY,
                KAFKA_UPLOAD_RATE_KEY, KAFKA_CLEAN_RATE_KEY, KAFKA_RECORDS_SEND_LIMIT_KEY,
                SENDER_CONNECTION_TIMEOUT_KEY);
    }

    public MainActivity() {
        super();
        isForcedDisconnected = false;
        mE4Connection = new DeviceServiceConnection<>(this, E4DeviceStatus.CREATOR, E4Service.class.getName());
        pebble2Connection = new DeviceServiceConnection<>(this, Pebble2DeviceStatus.CREATOR, Pebble2Service.class.getName());
        phoneConnection = new DeviceServiceConnection<>(this, PhoneSensorsDeviceStatus.CREATOR, PhoneSensorsService.class.getName());
        mConnections = new DeviceServiceConnection[] {mE4Connection, null, pebble2Connection, phoneConnection};
        mConnectionIsBound = new boolean[] {false, false, false, false};
        serverStatus = null;

        rowMap = new SparseIntArray(4);
        rowMap.put(R.id.row1, 0);
        rowMap.put(R.id.row2, 1);
        rowMap.put(R.id.row3, 2);
        rowMap.put(R.id.row4, 3);

        mTotalRecordsSent = new TimedInt[4];
        for (int i = 0; i < 4; i++) {
            mTotalRecordsSent[i] = new TimedInt();
        }

        bindServicesRunner = new Runnable() {
            @Override
            public void run() {
                if (!mConnectionIsBound[0]) {
                    Intent e4serviceIntent = new Intent(MainActivity.this, E4Service.class);
                    Bundle extras = new Bundle();
                    configureEmpatica(extras);
                    e4serviceIntent.putExtras(extras);

                    mE4Connection.bind(e4serviceIntent);
                    mConnectionIsBound[0] = true;
                }
                if (!mConnectionIsBound[2]) {
                    Intent pebble2Intent = new Intent(MainActivity.this, Pebble2Service.class);
                    Bundle extras = new Bundle();
                    configurePebble2(extras);
                    pebble2Intent.putExtras(extras);

                    pebble2Connection.bind(pebble2Intent);
                    mConnectionIsBound[2] = true;
                }
                if (!mConnectionIsBound[3]) {
                    Intent phoneIntent = new Intent(MainActivity.this, PhoneSensorsService.class);
                    Bundle extras = new Bundle();
                    configurePhoneSensors(extras);
                    phoneIntent.putExtras(extras);

                    phoneConnection.bind(phoneIntent);
                    mConnectionIsBound[3] = true;
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
                        logger.info("Bluetooth has turned on");
                        getHandler().postDelayed(mUIScheduler, uiRefreshRate);
                        startScanning();
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        logger.warn("Bluetooth is off");
                        getHandler().postDelayed(mUIScheduler, uiRefreshRate);
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

        // Not needed in API level 22.
        // checkBluetoothPermissions();

        // Check availability of Google Play Services
        if ( GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS ) {
            mFirebaseStatusIcon.setBackgroundResource(R.drawable.status_disconnected);
            mFirebaseMessage.setText(R.string.playServicesUnavailable);
        }
    }

    private void initializeViews() {
        mDeviceInputButtons = new Button[] {
                (Button) findViewById(R.id.inputDeviceNameButtonRow1),
                (Button) findViewById(R.id.inputDeviceNameButtonRow2),
                (Button) findViewById(R.id.inputDeviceNameButtonRow3),
                (Button) findViewById(R.id.inputDeviceNameButtonRow4)
        };

        // Firebase
        mFirebaseStatusIcon = findViewById(R.id.firebaseStatus);
        mFirebaseMessage = (TextView) findViewById( R.id.firebaseStatusMessage);
    }

    private void initializeRemoteConfig() {
        // TODO: disable developer mode in production
        radarConfiguration = RadarConfiguration.configure(true, R.xml.remote_config_defaults_template);
        radarConfiguration.onFetchComplete(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Once the config is successfully fetched it must be
                    // activated before newly fetched values are returned.
                    radarConfiguration.activateFetched();
                    if (mConnectionIsBound[0]) {
                        Bundle bundle = new Bundle();
                        configureEmpatica(bundle);
                        mE4Connection.updateConfiguration(bundle);
                    }
                    if (mConnectionIsBound[2]) {
                        Bundle bundle = new Bundle();
                        configurePebble2(bundle);
                        pebble2Connection.updateConfiguration(bundle);
                    }
                    if (mConnectionIsBound[3]) {
                        Bundle bundle = new Bundle();
                        configurePhoneSensors(bundle);
                        phoneConnection.updateConfiguration(bundle);
                    }
                    logger.info("Remote Config: Activate success.");
                    // Set global properties.
                    mFirebaseStatusIcon.setBackgroundResource(R.drawable.status_connected);
                    mFirebaseMessage.setText("Remote config fetched from the server ("
                            + timeFormat.format( System.currentTimeMillis() ) + ")");
                } else {
                    Toast.makeText(MainActivity.this, "Remote Config: Fetch Failed",
                            Toast.LENGTH_SHORT).show();
                    logger.info("Remote Config: Fetch failed. Stacktrace: {}", task.getException());
                }
            }
        });
    }

    @Override
    protected void onResume() {
        logger.info("mainActivity onResume");
        super.onResume();
        mHandler.postDelayed(bindServicesRunner, 300L);

        radarConfiguration.fetch();
    }

    @Override
    protected void onPause() {
        logger.info("mainActivity onPause");
        super.onPause();
        mHandler.removeCallbacks(mUIScheduler);
    }

    @Override
    protected void onStart() {
        logger.info("mainActivity onStart");
        super.onStart();
        registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(deviceFailedReceiver, new IntentFilter(DEVICE_CONNECT_FAILED));

        mHandlerThread = new HandlerThread("E4Service connection", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        synchronized (this) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }
        mHandler.post(mUIScheduler);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mConnections.length; i++) {
                    mConnectionIsBound[i] = false;
                }
            }
        });
    }

    @Override
    protected void onStop() {
        logger.info("mainActivity onStop");
        super.onStop();
        unregisterReceiver(deviceFailedReceiver);
        unregisterReceiver(bluetoothReceiver);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mConnections.length; i++) {
                    if (mConnectionIsBound[i]) {
                        mConnectionIsBound[i] = false;
                        mConnections[i].unbind();
                    }
                }
            }
        });
        mHandlerThread.quitSafely();
    }

    private synchronized Handler getHandler() {
        return mHandler;
    }

    private void disconnect() {
        for (int i = 0; i < mConnections.length; i++) {
            disconnect(i);
        }
    }

    private void disconnect(int row) {
        DeviceServiceConnection connection = mConnections[row];
        if (connection != null && connection.isRecording()) {
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
        for (int i = 0; i < mConnections.length; i++) {
            DeviceServiceConnection connection = mConnections[i];
            if (connection == null || !connection.hasService() || connection.isRecording()) {
                continue;
            }
            Set<String> acceptableIds;
            if (mInputDeviceKeys[i] != null && !mInputDeviceKeys[i].isEmpty()) {
                acceptableIds = Collections.singleton(mInputDeviceKeys[i]);
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
        mHandler.post(bindServicesRunner);
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
                        for (int i = 0; i < mConnections.length; i++) {
                            if (mConnections[i] != connection) {
                                continue;
                            }
                            // Reject if device name inputted does not equal device nameA
                            if (mInputDeviceKeys[i] != null && !connection.isAllowedDevice(mInputDeviceKeys[i])) {
                                logger.info("Device name '{}' is not equal to '{}'", connection.getDeviceName(), mInputDeviceKeys[i]);
                                Boast.makeText(MainActivity.this, String.format("Device '%s' rejected", connection.getDeviceName()), Toast.LENGTH_LONG).show();
                                disconnect();
                            }
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

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mUIUpdater.update();
                    DeviceServiceConnection connection = mConnections[row];
                    if (connection == mE4Connection) {
                        new E4HeartbeatToast(MainActivity.this).execute(connection);
                    } else if (connection == pebble2Connection) {
                        new Pebble2HeartbeatToast(MainActivity.this).execute(connection);
                    }
                } catch (RemoteException e) {
                    logger.warn("Failed to update view with device data");
                }
            }
        });

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
        mTotalRecordsSent[row].add(numberOfRecords);
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
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String oldValue = mInputDeviceKeys[row];
                mInputDeviceKeys[row] = input.getText().toString();
                mDeviceInputButtons[row].setText( mInputDeviceKeys[row] );

                // Do NOT disconnect if input has not changed, is empty or equals the connected device.
                if (!mInputDeviceKeys[row].equals(oldValue) &&
                    !mInputDeviceKeys[row].isEmpty()        &&
                    !mConnections[row].isAllowedDevice( mInputDeviceKeys[row] ) )
                {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (mConnections[row].isRecording()) {
                                    mConnections[row].stopRecording();
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

    private int getRow(DeviceServiceConnection<?> connection) {
        for (int i = 0; i < mConnections.length; i++) {
            if (mConnections[i] != null && mConnections[i].getServiceClassName().equals(connection.getServiceClassName())) {
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
        return mTotalRecordsSent[row];
    }

    public String getLatestTopicSent() {
        return latestTopicSent;
    }

    public TimedInt getLatestNumberOfRecordsSent() {
        return latestNumberOfRecordsSent;
    }

    public DeviceServiceConnection<?> getConnection(int i) {
        return mConnections[i];
    }
}