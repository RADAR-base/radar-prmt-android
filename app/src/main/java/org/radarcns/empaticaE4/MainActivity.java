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
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;

import org.radarcns.R;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import static org.radarcns.empaticaE4.E4Service.DEVICE_CONNECT_FAILED;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_NAME;
import static org.radarcns.empaticaE4.E4Service.SERVER_STATUS_CHANGED;

public class MainActivity extends AppCompatActivity {
    protected final static Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_PERMISSIONS = 2;
    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView serverStatusLabel;
    private TextView emptyDevices;
    private Button stopButton;
    private RelativeLayout dataCnt;
    private RelativeLayout deviceView;
    private TextView deviceLabel;
    private Map<E4ServiceConnection, Button> deviceButtons;

    protected long uiRefreshRate;
    protected Handler mHandler;
    protected Runnable mUIScheduler;
    private DeviceUIUpdater mUIUpdater;
    protected boolean isForcedDisconnected = false;

    /** Defines callbacks for service binding, passed to bindService() */
    protected E4ServiceConnection mConnection;
    protected E4ServiceConnection activeConnection;
    
    private final BroadcastReceiver serverStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SERVER_STATUS_CHANGED)) {
                final ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];
                updateServerStatus(status);
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
    private TextView accelSensorLabel;
    private TextView bvpSensorLabel;
    private TextView edaSensorLabel;
    private TextView temperatureSensorLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnection = new E4ServiceConnection(this);

        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (RelativeLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        deviceView = (RelativeLayout) findViewById(R.id.deviceNames);
        emptyDevices = (TextView) findViewById(R.id.emptyDevices);
        serverStatusLabel = (TextView) findViewById(R.id.serverStatus);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceLabel = (TextView) findViewById(R.id.activeDeviceLabel);
        deviceButtons = new HashMap<>();
        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setVisibility(View.INVISIBLE);

        accelSensorLabel = (TextView) findViewById(R.id.acceleration_sensor);
        bvpSensorLabel = (TextView) findViewById(R.id.bvp_sensor);
        edaSensorLabel = (TextView) findViewById(R.id.eda_sensor);
        temperatureSensorLabel = (TextView) findViewById(R.id.temperature_sensor);

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

    void bindToEmpatica(E4ServiceConnection connection) {
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

    void addDeviceButton(final E4ServiceConnection connection) {
        String name = connection.getDeviceName();
        if (name != null) {
            emptyDevices.setVisibility(View.INVISIBLE);
            Button btn = new Button(this);
            btn.setLayoutParams(new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
            btn.setText(name);
            btn.setId(View.NO_ID);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    synchronized (MainActivity.this) {
                        activeConnection = connection;
                    }
                }
            });
            deviceView.addView(btn);
            deviceButtons.put(connection, btn);
        }
    }

    protected synchronized E4ServiceConnection getActiveConnection() {
        return activeConnection;
    }

    void serviceConnected(final E4ServiceConnection connection) {
        synchronized (this) {
            if (activeConnection == null) {
                activeConnection = connection;
            }
        }
        try {
            ServerStatusListener.Status status = connection.getServerStatus();
            logger.info("Initial server status: {}", status);
            updateServerStatus(status);
        } catch (RemoteException e) {
            logger.warn("Failed to update UI server status");
        }
        runOnUiThread(new Runnable() {
              @Override
              public void run() {
                  Button showButton = (Button) findViewById(R.id.showButton);
                  showButton.setVisibility(View.VISIBLE);
                  showButton.setOnClickListener(new View.OnClickListener() {
                      public void onClick(View v) {
                          E4ServiceConnection active = getActiveConnection();
                          if (active != null) {
                              new E4HeartbeatToast(MainActivity.this).execute(active);
                          }
                      }
                  });
              }
        });
        startScanning();
    }

    void updateServerStatus(final ServerStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case READY:
                    case DISABLED:
                        serverStatusLabel.setVisibility(View.INVISIBLE);
                        break;
                    case CONNECTED:
                        serverStatusLabel.setText("Server connected");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case DISCONNECTED:
                        serverStatusLabel.setText("Server disconnected");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case CONNECTING:
                        serverStatusLabel.setText("Connecting to server");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case UPLOADING:
                        serverStatusLabel.setText("Uploading");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                }
            }
        });
    }

    public void deviceStatusUpdated(final E4ServiceConnection connection, final DeviceStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case CONNECTED:
                        addDeviceButton(connection);

                        synchronized (MainActivity.this) {
                            if (activeConnection == null) {
                                activeConnection = connection;
                            }
                        }
                        updateLabel(stopButton, "Stop Recording");
                        dataCnt.setVisibility(View.VISIBLE);
                        statusLabel.setText("CONNECTED");
                        startScanning();
                        break;
                    case CONNECTING:
                        updateLabel(stopButton, "Stop Recording");
                        statusLabel.setText("CONNECTING");
                        break;
                    case DISCONNECTED:
                        Button btn = deviceButtons.remove(connection);
                        deviceView.removeView(btn);
                        synchronized (MainActivity.this) {
                            if (connection.equals(activeConnection)) {
                                activeConnection = null;
                            }
                        }
                        if (deviceButtons.isEmpty()) {
                            emptyDevices.setVisibility(View.VISIBLE);
                        }
                        dataCnt.setVisibility(View.INVISIBLE);
                        statusLabel.setText("DISCONNECTED");
                        break;
                    case READY:
                        statusLabel.setText("Scanning...");
                        stopButton.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                isForcedDisconnected = !isForcedDisconnected;
                                if (isForcedDisconnected) {
                                    disconnect();
                                    stopButton.setText("Start Recording");
                                } else {
                                    startScanning();
                                    stopButton.setText("Stop Recording");
                                }
                            }
                        });
                        updateLabel(stopButton, "Stop Recording");
                        stopButton.setVisibility(View.VISIBLE);
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

    protected void checkBluetoothPermissions() {
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
                updateLabel(statusLabel, "Cannot connect to Empatica E4DeviceManager without location permissions");
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
            deviceLabel.setText(deviceName);
            float[] acceleration = deviceData.getAcceleration();
            setText(accel_xLabel, acceleration[0], "g", doubleDecimal);
            setText(accel_yLabel, acceleration[1], "g", doubleDecimal);
            setText(accel_zLabel, acceleration[2], "g", doubleDecimal);
            setText(bvpLabel, deviceData.getBloodVolumePulse(), "\u00B5W", singleDecimal);
            setText(edaLabel, deviceData.getElectroDermalActivity(), "\u00B5S", doubleDecimal);
            setText(ibiLabel, deviceData.getInterBeatInterval(), "s", doubleDecimal);
            setText(temperatureLabel, deviceData.getTemperature(), "\u2103", singleDecimal);
            setText(batteryLabel, 100*deviceData.getBatteryLevel(), "%", noDecimals);

            Map<EmpaSensorType, EmpaSensorStatus> sensorStatus = deviceData.getSensorStatus();
            if (sensorStatus.containsKey(EmpaSensorType.ACC)) {
                accelSensorLabel.setText(EmpaSensorType.ACC.name());
            }
            if (sensorStatus.containsKey(EmpaSensorType.TEMP)) {
                temperatureLabel.setText(EmpaSensorType.TEMP.name());
            }
            if (sensorStatus.containsKey(EmpaSensorType.BVP)) {
                bvpSensorLabel.setText(EmpaSensorType.BVP.name());
            }
            if (sensorStatus.containsKey(EmpaSensorType.GSR)) {
                edaSensorLabel.setText(EmpaSensorType.GSR.name());
            }
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
}