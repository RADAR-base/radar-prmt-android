package org.radarcns.empaticaE4;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.R;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import static org.radarcns.empaticaE4.E4Service.DEVICE_CONNECT_FAILED;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_NAME;
import static org.radarcns.empaticaE4.E4Service.SERVER_STATUS_CHANGED;

public class MainActivity extends AppCompatActivity {
    private final static Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_PERMISSIONS = 2;

    private long uiRefreshRate;
    private Handler mHandler;
    private Runnable mUIScheduler;
    private DeviceUIUpdater mUIUpdater;
    private boolean isForcedDisconnected = false;

    /** Defines callbacks for service binding, passed to bindService() */
    private E4ServiceConnection mConnection;
    private E4ServiceConnection activeConnection;

    /** New UI **/
    private TextView[] mDeviceNameLabels;
    private View[] mStatusIcons;
    private View[] mServerStatusIcons;
    private TextView[] mTemperatureLabels;
    private TextView[] mBatteryLabels;


    private final BroadcastReceiver serverStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SERVER_STATUS_CHANGED)) {
                final ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];
                updateServerStatus(status, 0);
            }
        }
    };

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    logger.info("Bluetooth has turned on");
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    logger.warn("Bluetooth is off");
                }
                startScanning();
            }
        }
    };

    private final BroadcastReceiver deviceFailedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            if (intent.getAction().equals(DEVICE_CONNECT_FAILED)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Cannot connect to device " + intent.getStringExtra(DEVICE_STATUS_NAME), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnection = new E4ServiceConnection(this);

        setContentView(R.layout.activity_overview);

        // Create arrays of labels. Fixed to four rows
        mDeviceNameLabels = new TextView[] {
                (TextView) findViewById(R.id.deviceNameRow1),
                (TextView) findViewById(R.id.deviceNameRow2),
                (TextView) findViewById(R.id.deviceNameRow3),
                (TextView) findViewById(R.id.deviceNameRow4)
        };

        mStatusIcons = new View[] {
                findViewById(R.id.statusRow1),
                findViewById(R.id.statusRow2),
                findViewById(R.id.statusRow3),
                findViewById(R.id.statusRow4)
        };

        mServerStatusIcons = new View[] {
                findViewById(R.id.statusServerRow1),
                findViewById(R.id.statusServerRow2),
                findViewById(R.id.statusServerRow3),
                findViewById(R.id.statusServerRow4)
        };

        mTemperatureLabels = new TextView[] {
                (TextView) findViewById(R.id.temperatureRow1),
                (TextView) findViewById(R.id.temperatureRow2),
                (TextView) findViewById(R.id.temperatureRow3),
                (TextView) findViewById(R.id.temperatureRow4)
        };

        mBatteryLabels = new TextView[] {
                (TextView) findViewById(R.id.batteryRow1),
                (TextView) findViewById(R.id.batteryRow2),
                (TextView) findViewById(R.id.batteryRow3),
                (TextView) findViewById(R.id.batteryRow4)
        };

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);
        mHandler = new Handler();

        mUIUpdater = new DeviceUIUpdater();
        mUIScheduler = new Runnable() {
            @Override
            public void run() {
                try {
                    E4ServiceConnection connection = getActiveConnection();
                    if (connection != null) {
                        mUIUpdater.updateWithData(connection);
                    }
                } catch (RemoteException e) {
                    logger.warn("Failed to update device data", e);
                } finally {
                    mHandler.postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

        isForcedDisconnected = false;
        checkBluetoothPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mUIScheduler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUIScheduler);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, bluetoothFilter);
        IntentFilter serverFilter = new IntentFilter(E4Service.SERVER_STATUS_CHANGED);
        registerReceiver(serverStatusListener, serverFilter);
        IntentFilter failedFilter = new IntentFilter(E4Service.DEVICE_CONNECT_FAILED);
        registerReceiver(deviceFailedReceiver, failedFilter);
        bindToEmpatica(mConnection);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(serverStatusListener);
        unregisterReceiver(deviceFailedReceiver);
        unregisterReceiver(bluetoothReceiver);
        unbindService(mConnection);
        mConnection.close();
    }

    private void disconnect() {
        if (mConnection.isRecording()) {
            try {
                mConnection.stopRecording();
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
        if (mConnection.isScanning()) {
            return;
        }
        if (mConnection.hasService() && !mConnection.isRecording()) {
            try {
                mConnection.startRecording();
            } catch (RemoteException e) {
                logger.error("Failed to start recording", e);
            }
        }
    }

    public void bindToEmpatica(E4ServiceConnection connection) {
        logger.info("Intending to start E4 service");

        Intent e4serviceIntent = new Intent(this, E4Service.class);
        e4serviceIntent.putExtra("kafka_rest_proxy_url", getString(R.string.kafka_rest_proxy_url));
        e4serviceIntent.putExtra("schema_registry_url", getString(R.string.schema_registry_url));
        e4serviceIntent.putExtra("group_id", getString(R.string.group_id));
        e4serviceIntent.putExtra("empatica_api_key", getString(R.string.apikey));
        startService(e4serviceIntent);
        bindService(e4serviceIntent, connection, Context.BIND_ABOVE_CLIENT);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    private synchronized E4ServiceConnection getActiveConnection() {
        return activeConnection;
    }

    public void serviceConnected(final E4ServiceConnection connection) {
        synchronized (this) {
            if (activeConnection == null) {
                activeConnection = connection;
            }
        }
        try {
            ServerStatusListener.Status status = connection.getServerStatus();
            logger.info("Initial server status: {}", status);
            updateServerStatus(status, 0);
        } catch (RemoteException e) {
            logger.warn("Failed to update UI server status");
        }

        startScanning();
    }

    public void deviceStatusUpdated(final E4ServiceConnection connection, final DeviceStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, status.toString(), Toast.LENGTH_SHORT).show();
                switch (status) {
                    case CONNECTED:
//                        addDeviceButton(connection);

                        synchronized (MainActivity.this) {
                            if (activeConnection == null) {
                                activeConnection = connection;
                            }
                        }
//                        statusLabel.setText("CONNECTED");
                        startScanning();
                        break;
                    case CONNECTING:
//                        statusLabel.setText("CONNECTING");
                        break;
                    case DISCONNECTED:

                        synchronized (MainActivity.this) {
                            if (connection.equals(activeConnection)) {
                                activeConnection = null;
                            }
                        }

//                        statusLabel.setText("DISCONNECTED");
                        break;
                    case READY:
//                        statusLabel.setText("Scanning...");
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
        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};

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
                Toast.makeText(this, "Cannot connect to Empatica E4DeviceManager without location permissions", Toast.LENGTH_LONG).show();
            }
        }
    }

    public class DeviceUIUpdater implements Runnable {
        final DecimalFormat singleDecimal = new DecimalFormat("0.0");
        final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
        final DecimalFormat noDecimals = new DecimalFormat("0");
        E4DeviceStatus deviceData = null;
        String deviceName = null;

        public void updateWithData(@NonNull E4ServiceConnection connection) throws RemoteException {
            deviceData = connection.getDeviceData();
            deviceName = connection.getDeviceName();
            runOnUiThread(this);
        }

        @Override
        public void run() {
            if (deviceData == null) {
                return;
            }
            updateRow(deviceData, 0);
            updateDeviceName(deviceName, 0);
        }

        void setText(TextView label, float value, String suffix, DecimalFormat formatter) {
            if (Float.isNaN(value)) {
                // em dash
                label.setText("\u2014");
            } else {
                label.setText(formatter.format(value) + " " + suffix);
            }
        }
    }


    public void connectDevice(View v) {
        int rowIndex = getRowIndexFromView(v);

        // some test code, updating with random data
//        Random generator = new Random();
//        updateDeviceName(String.valueOf( generator.hashCode() ), rowIndex);
    }

    public void showDetails(View v) {
        int rowIndex = getRowIndexFromView(v);

        // some test code, updating with random data
//        E4DeviceStatus deviceData = new E4DeviceStatus();
//
//        Random generator = new Random();
//        deviceData.setTemperature(  generator.nextFloat()*100 );
//        deviceData.setBatteryLevel( generator.nextFloat() );
//        deviceData.setStatus( new DeviceStatusListener.Status[] {CONNECTED,DISCONNECTED,READY,CONNECTING}[generator.nextInt(4)] );
//
//        updateRow(deviceData, rowIndex);
    }

    private int getRowIndexFromView(View v) {
        // Assume all elements are direct descendants from the TableRow
        View parent = (View) v.getParent();
        switch ( parent.getId() ) {

            case R.id.row1:
                return 0;

            case R.id.row2:
                return 1;

            case R.id.row3:
                return 2;

            case R.id.row4:
                return 3;

            default:
                return -1; // TODO: throw exception
        }
    }

    public void updateDeviceName(String deviceName, int row) {
        // TODO: restrict n_characters of deviceName
        mDeviceNameLabels[row].setText(deviceName);
    }

    /**
     * Updates a row with the deviceData
     * @param deviceData
     * @param row           Row number
     */
    public void updateRow(E4DeviceStatus deviceData, int row ) {
        final DecimalFormat singleDecimal = new DecimalFormat("0.0");
        final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
        final DecimalFormat noDecimals = new DecimalFormat("0");

        // Connection status. Change icon used.
        switch (deviceData.getStatus()) {
            case CONNECTED:
                mStatusIcons[row].setBackgroundResource( R.drawable.status_connected );
                break;
            case DISCONNECTED:
                mStatusIcons[row].setBackgroundResource( R.drawable.status_disconnected );
                break;
            case READY:
            case CONNECTING:
                mStatusIcons[row].setBackgroundResource( R.drawable.status_searching );
                break;
            default:
                mStatusIcons[row].setBackgroundResource( R.drawable.status_searching );
        }

        // Temperature
        setText(mTemperatureLabels[row], deviceData.getTemperature(), "\u2103", singleDecimal);

        // Battery
        setText(mBatteryLabels[row], 100*deviceData.getBatteryLevel(), "%", noDecimals);

    }

    private void setText(TextView label, float value, String suffix, DecimalFormat formatter) {
        if (Float.isNaN(value)) {
            // em dash
            label.setText("\u2014");
        } else {
            label.setText(formatter.format(value) + " " + suffix);
        }
    }

    public void updateServerStatus( final ServerStatusListener.Status status, final int row ) {
        // Connection status. Change icon used.\

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case CONNECTED:
                        mServerStatusIcons[row].setBackgroundResource( R.drawable.status_connected );
                        break;
                    case DISCONNECTED:
                    case DISABLED:
                        mServerStatusIcons[row].setBackgroundResource( R.drawable.status_disconnected );
                        break;
                    case READY:
                    case CONNECTING:
                        mServerStatusIcons[row].setBackgroundResource( R.drawable.status_searching );
                        break;
                    case UPLOADING:
                        mServerStatusIcons[row].setBackgroundResource( R.drawable.status_searching );
                        break;
                    default:
                        mServerStatusIcons[row].setBackgroundResource( R.drawable.status_searching );
                }
            }
        });
    }
}