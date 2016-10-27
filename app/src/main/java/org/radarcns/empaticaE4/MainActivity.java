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

import org.radarcns.data.Record;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private Button stopButton;
    private RelativeLayout dataCnt;
    private RelativeLayout deviceView;
    private Map<E4ServiceConnection, Button> deviceButtons;

    private long uiRefreshRate;
    private Handler mHandler;
    private Runnable mUIScheduler;
    private DeviceUIUpdater mUIUpdater;
    private boolean isDisconnected = false;

    private boolean waitingForPermission;

    /** Defines callbacks for service binding, passed to bindService() */
    private final E4ServiceConnection[] mConnections = {
            new E4ServiceConnection(this, 0), new E4ServiceConnection(this, 1), new E4ServiceConnection(this, 2), new E4ServiceConnection(this, 3)
    };
    private E4ServiceConnection activeConnection;
    private final BroadcastReceiver serverStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SERVER_STATUS_CHANGED)) {
                final ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];
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
                    enableEmpatica();
                } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    logger.warn("Bluetooth is turning off");
                    disconnect();
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    logger.warn("Bluetooth is off");
                    enableBt();
                }
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
        deviceButtons = new HashMap<>();
        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setVisibility(View.INVISIBLE);

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);
        mHandler = new Handler();

        mUIUpdater = new DeviceUIUpdater();
        mUIScheduler = new Runnable() {
            @Override
            public void run() {
                try {
                    if (activeConnection != null) {
                        mUIUpdater.updateWithData(activeConnection);
                    }
                } catch (RemoteException e) {
                    logger.warn("Failed to update device data", e);
                } finally {
                    mHandler.postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

        checkBluetoothPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mUIScheduler);
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, bluetoothFilter);
        IntentFilter serverFilter = new IntentFilter(E4Service.SERVER_STATUS_CHANGED);
        registerReceiver(serverStatusListener, serverFilter);
        IntentFilter failedFilter = new IntentFilter(E4Service.DEVICE_CONNECT_FAILED);
        registerReceiver(deviceFailedReceiver, failedFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUIScheduler);
        unregisterReceiver(serverStatusListener);
        unregisterReceiver(deviceFailedReceiver);
        unregisterReceiver(bluetoothReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        enableEmpatica();
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnect();
    }

    private void disconnect() {
        for (E4ServiceConnection mConnection : mConnections) {
            if (mConnection.hasService()) {
                unbindService(mConnection);
                mConnection.close();
            }
        }
    }

    private int freeServiceNumber() {
        for (int i = 0; i < mConnections.length; i++) {
            if (!mConnections[i].hasService()) {
                return i;
            }
        }
        return -1;
    }

    boolean enableEmpatica() {
        if (waitingForPermission) {
            return true;
        }

        int clsNumber = freeServiceNumber();
        if (clsNumber == -1) {
            return false;
        }
        Class<? extends E4Service> serviceCls = mConnections[clsNumber].serviceClass();
        logger.info("Intending to start E4 service");
        startService(new Intent(this, serviceCls));

        Intent e4serviceIntent = new Intent(this, serviceCls);
        e4serviceIntent.putExtra("kafka_rest_proxy_url", getString(R.string.kafka_rest_proxy_url));
        e4serviceIntent.putExtra("schema_registry_url", getString(R.string.schema_registry_url));
        e4serviceIntent.putExtra("group_id", getString(R.string.group_id));
        e4serviceIntent.putExtra("empatica_api_key", getString(R.string.apikey));
        bindService(e4serviceIntent, mConnections[clsNumber], Context.BIND_ABOVE_CLIENT);
        return true;
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
                    activeConnection = connection;
                }
            });
            deviceView.addView(btn);
            deviceButtons.put(connection, btn);
        }
    }

    void serviceConnected(final E4ServiceConnection connection) {
        Button showButton = (Button) findViewById(R.id.showButton);
        showButton.setVisibility(View.VISIBLE);
        showButton.setOnClickListener(new View.OnClickListener() {
            final DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            final DecimalFormat singleDecimal = new DecimalFormat("0.0");

            public void onClick(View v) {
                try {
                    List<Record<MeasurementKey, EmpaticaE4InterBeatInterval>> measurements = connection.getRecords(E4Topics.getInstance().getInterBeatIntervalTopic(), 25);
                    if (!measurements.isEmpty()) {
                        StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                        for (Record<MeasurementKey, EmpaticaE4InterBeatInterval> measurement : measurements) {
                            sb.append(timeFormat.format(1000d * measurement.value.getTime()));
                            sb.append(": ");
                            sb.append(singleDecimal.format(60d / measurement.value.getInterBeatInterval()));
                            sb.append('\n');
                        }
                        String view = sb.toString();
                        Toast.makeText(MainActivity.this, view, Toast.LENGTH_LONG).show();
                        logger.info("Data:\n{}", view);
                    } else {
                        Toast.makeText(MainActivity.this, "No heart rate collected yet.", Toast.LENGTH_SHORT).show();
                    }
                } catch (RemoteException | IOException e) {
                    Toast.makeText(MainActivity.this, "Failed to retrieve heart rates.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void deviceStatusUpdated(final E4ServiceConnection connection, final E4DeviceStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case CONNECTED:
                        addDeviceButton(connection);

                        if (activeConnection == null) {
                            activeConnection = connection;
                        }
                        updateLabel(stopButton, "Stop Recording");
                        isDisconnected = false;
                        dataCnt.setVisibility(View.VISIBLE);
                        statusLabel.setText("CONNECTED");
                        enableEmpatica();
                        break;
                    case CONNECTING:
                        updateLabel(stopButton, "Stop Recording");
                        isDisconnected = false;
                        statusLabel.setText("CONNECTING");
                        break;
                    case DISCONNECTED:
                        Button btn = deviceButtons.remove(connection);
                        deviceView.removeView(btn);
                        if (connection.equals(activeConnection)) {
                            activeConnection = null;
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
                                if (isDisconnected) {
                                    enableEmpatica();
                                } else {
                                    disconnect();
                                    isDisconnected = true;
                                }

                            }
                        });
                        updateLabel(stopButton, "Stop Recording");
                        isDisconnected = false;
                        stopButton.setVisibility(View.VISIBLE);
                        break;
                }
            }
        });
    }

    private void checkBluetoothPermissions() {
        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};

        waitingForPermission = false;
        isDisconnected = false;
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

    void enableBt() {
        BluetoothAdapter btAdaptor = BluetoothAdapter.getDefaultAdapter();
        if (!btAdaptor.isEnabled() && btAdaptor.getState() != BluetoothAdapter.STATE_TURNING_ON) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(btIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted.
                waitingForPermission = false;
                enableEmpatica();
            } else {
                // User refused to grant permission.
                updateLabel(statusLabel, "Cannot connect to Empatica E4DeviceManager without location permissions");
            }
        }
    }

    private class DeviceUIUpdater implements Runnable {
        final DecimalFormat singleDecimal = new DecimalFormat("0.0");
        final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
        final DecimalFormat noDecimals = new DecimalFormat("0");
        E4DeviceStatus deviceData = null;

        void updateWithData(@NonNull E4ServiceConnection connection) throws RemoteException {
            deviceData = connection.getDeviceData();
            runOnUiThread(this);
        }

        @Override
        public void run() {
            if (deviceData == null) {
                return;
            }
            float[] acceleration = deviceData.getAcceleration();
            setText(accel_xLabel, acceleration[0], "g", singleDecimal);
            setText(accel_yLabel, acceleration[1], "g", singleDecimal);
            setText(accel_zLabel, acceleration[2], "g", singleDecimal);
            setText(bvpLabel, deviceData.getBloodVolumePulse(), "\u00B5W", singleDecimal);
            setText(edaLabel, deviceData.getElectroDermalActivity(), "\u00B5S", doubleDecimal);
            setText(ibiLabel, deviceData.getInterBeatInterval(), "s", doubleDecimal);
            setText(temperatureLabel, deviceData.getTemperature(), "\u2103", singleDecimal);
            setText(batteryLabel, 100*deviceData.getBatteryLevel(), "%", noDecimals);
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