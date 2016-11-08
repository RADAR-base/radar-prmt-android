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
import android.os.HandlerThread;
import android.os.Process;
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
    private final static Logger logger = LoggerFactory.getLogger(MainActivity.class);

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
    private TextView accelSensorLabel;
    private TextView bvpSensorLabel;
    private TextView edaSensorLabel;
    private TextView temperatureSensorLabel;
    private Button stopButton;
    private RelativeLayout dataCnt;
    private RelativeLayout deviceView;
    private TextView deviceLabel;
    private Map<E4ServiceConnection, Button> deviceButtons;

    private long uiRefreshRate;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private Runnable mUIScheduler;
    private DeviceUIUpdater mUIUpdater;
    private boolean isForcedDisconnected;
    private boolean mConnectionIsBound;

    /** Defines callbacks for service binding, passed to bindService() */
    private final E4ServiceConnection mConnection;
    private final BroadcastReceiver serverStatusListener;
    private final BroadcastReceiver bluetoothReceiver;
    private final BroadcastReceiver deviceFailedReceiver;

    private E4ServiceConnection activeConnection;

    public MainActivity() {
        super();
        isForcedDisconnected = false;
        mConnection = new E4ServiceConnection(this);
        serverStatusListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(SERVER_STATUS_CHANGED)) {
                    final ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];
                    updateServerStatus(status);
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
                    if (state == BluetoothAdapter.STATE_ON) {
                        logger.info("Bluetooth has turned on");
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
                            Toast.makeText(MainActivity.this, "Cannot connect to device " + intent.getStringExtra(DEVICE_STATUS_NAME), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        accelSensorLabel = (TextView) findViewById(R.id.acceleration_sensor);
        bvpSensorLabel = (TextView) findViewById(R.id.bvp_sensor);
        edaSensorLabel = (TextView) findViewById(R.id.eda_sensor);
        temperatureSensorLabel = (TextView) findViewById(R.id.temperature_sensor);

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);

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
                    getHandler().postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

        checkBluetoothPermissions();
    }

    @Override
    protected void onResume() {
        logger.info("mainActivity onResume");
        super.onResume();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mConnectionIsBound) {
                    mConnection.bind(
                            getString(R.string.kafka_rest_proxy_url), getString(R.string.schema_registry_url),
                            getString(R.string.group_id), getString(R.string.apikey));
                    mConnectionIsBound = true;
                }
            }
        }, 250L);
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
        registerReceiver(serverStatusListener, new IntentFilter(E4Service.SERVER_STATUS_CHANGED));
        registerReceiver(deviceFailedReceiver, new IntentFilter(E4Service.DEVICE_CONNECT_FAILED));

        mHandlerThread = new HandlerThread("E4Service connection", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        synchronized (this) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mConnectionIsBound = false;
            }
        });
    }

    @Override
    protected void onStop() {
        logger.info("mainActivity onStop");
        super.onStop();
        unregisterReceiver(serverStatusListener);
        unregisterReceiver(deviceFailedReceiver);
        unregisterReceiver(bluetoothReceiver);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mConnectionIsBound) {
                    mConnection.unbind();
                    mConnectionIsBound = false;
                }
            }
        });
        mHandlerThread.quitSafely();
    }

    private synchronized Handler getHandler() {
        return mHandler;
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

    synchronized E4ServiceConnection getActiveConnection() {
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

    synchronized void serviceDisconnected(final E4ServiceConnection connection) {
        if (connection == activeConnection) {
            activeConnection = null;
        }
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
                        serverStatusLabel.setText(R.string.server_connected);
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case DISCONNECTED:
                        serverStatusLabel.setText(R.string.server_disconnected);
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case CONNECTING:
                        serverStatusLabel.setText(R.string.server_connecting);
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        break;
                    case UPLOADING:
                        serverStatusLabel.setText(R.string.server_uploading);
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
                        stopButton.setText(R.string.stop_recording);
                        dataCnt.setVisibility(View.VISIBLE);
                        statusLabel.setText(R.string.device_connected);
                        startScanning();
                        break;
                    case CONNECTING:
                        stopButton.setText(R.string.stop_recording);
                        statusLabel.setText(R.string.device_connecting);
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
                        statusLabel.setText(R.string.device_disconnected);
                        break;
                    case READY:
                        statusLabel.setText(R.string.device_scanning);
                        stopButton.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                isForcedDisconnected = !isForcedDisconnected;
                                if (isForcedDisconnected) {
                                    disconnect();
                                    stopButton.setText(R.string.start_recording);
                                } else {
                                    startScanning();
                                    stopButton.setText(R.string.stop_recording);
                                }
                            }
                        });
                        stopButton.setText(R.string.stop_recording);
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(R.string.bluetooth_permission_failure);
                    }
                });
            }
        }
    }

    private class DeviceUIUpdater implements Runnable {
        final DecimalFormat singleDecimal = new DecimalFormat("0.0");
        final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
        final DecimalFormat noDecimals = new DecimalFormat("0");
        E4DeviceStatus deviceData = null;
        String deviceName = null;

        void updateWithData(@NonNull E4ServiceConnection connection) throws RemoteException {
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
                temperatureSensorLabel.setText(EmpaSensorType.TEMP.name());
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