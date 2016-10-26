package org.radarcns.empaticaE4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ServerStatusListener, E4DeviceStatusListener {
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
    private Map<String, Button> deviceButtons;

    private long uiRefreshRate;
    private Handler mHandler;
    private Runnable mUIScheduler;
    private Runnable mUIUpdater;
    private boolean isDisconnected = false;

    private boolean waitingForPermission;
    private E4DeviceManager activeDevice = null;

    /** Defines callbacks for service binding, passed to bindService() */
    private final E4ServiceConnection[] mConnections = {
            new E4ServiceConnection(this, 0), new E4ServiceConnection(this, 1), new E4ServiceConnection(this, 2), new E4ServiceConnection(this, 3)
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
        mUIUpdater = new Runnable() {
            final DecimalFormat singleDecimal = new DecimalFormat("0.0");
            final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
            final DecimalFormat noDecimals = new DecimalFormat("0");
            @Override
            public void run() {
                if (activeDevice == null) {
                    return;
                }
                float[] acceleration = activeDevice.getLatestAcceleration();
                setText(accel_xLabel, acceleration[0], "g", singleDecimal);
                setText(accel_yLabel, acceleration[1], "g", singleDecimal);
                setText(accel_zLabel, acceleration[2], "g", singleDecimal);
                setText(bvpLabel, activeDevice.getLatestBloodVolumePulse(), "\u00B5W", singleDecimal);
                setText(edaLabel, activeDevice.getLatestElectroDermalActivity(), "\u00B5S", doubleDecimal);
                setText(ibiLabel, activeDevice.getLatestInterBeatInterval(), "s", doubleDecimal);
                setText(temperatureLabel, activeDevice.getLatestTemperature(), "\u2103", singleDecimal);
                setText(batteryLabel, 100*activeDevice.getLatestBatteryLevel(), "%", noDecimals);
            }

            void setText(TextView label, float value, String suffix, DecimalFormat formatter) {
                if (Float.isNaN(value)) {
                    // em dash
                    label.setText("\u2014");
                } else {
                    label.setText(formatter.format(value) + " " + suffix);
                }
            }
        };
        mUIScheduler = new Runnable() {
            @Override
            public void run() {
                try {
                    runOnUiThread(mUIUpdater);
                } finally {
                    mHandler.postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

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
        Intent e4serviceIntent = new Intent(this, serviceCls);
        e4serviceIntent.putExtra("kafka_rest_proxy_url", getString(R.string.kafka_rest_proxy_url));
        e4serviceIntent.putExtra("schema_registry_url", getString(R.string.schema_registry_url));
        e4serviceIntent.putExtra("group_id", getString(R.string.group_id));
        e4serviceIntent.putExtra("empatica_api_key", getString(R.string.apikey));
        startService(e4serviceIntent);
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


    void addDeviceButton(final E4DeviceManager manager) {
        String name = manager.getDeviceName();
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
                    activeDevice = manager;
                    mUIUpdater.run();
                }
            });
            deviceView.addView(btn);
            deviceButtons.put(name, btn);
        }
    }

    @Override
    public void deviceStatusUpdated(E4DeviceManager deviceManager, E4DeviceStatusListener.Status status) {
        switch (status) {
            case CONNECTED:
                addDeviceButton(deviceManager);

                if (activeDevice == null) {
                    activeDevice = deviceManager;
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
                if (deviceManager != null) {
                    Button btn = deviceButtons.remove(deviceManager.getDeviceName());
                    deviceView.removeView(btn);
                    if (deviceManager.equals(activeDevice)) {
                        activeDevice = null;
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
                        if (isDisconnected) {
                            enableEmpatica();
                        } else {
                            for (E4ServiceConnection mConnection : mConnections) {
                                mConnection.disconnect();
                            }
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

    @Override
    public void deviceFailedToConnect(String name) {
        Toast.makeText(this, "Cannot connect to device " + name, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void updateServerStatus(final ServerStatusListener.Status status) {
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