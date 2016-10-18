package org.radarcns.empaticaE4;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.radarcns.android.MeasurementIterator;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

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
    private TextView deviceNameLabel;
    private TextView serverStatusLabel;
    private Button stopButton;
    private Button reconnectButton;
    private RelativeLayout dataCnt;

    private long uiRefreshRate;
    private Handler mHandler;
    private Runnable mUIScheduler;
    private Runnable mUIUpdater;
    private E4Topics topics;
    private E4Service e4Service;
    private boolean mBound;
    private boolean waitingForPermission;
    private boolean waitingForBind;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to the running Service, cast the IBinder and get instance
            E4Service.LocalBinder binder = (E4Service.LocalBinder) service;
            e4Service = binder.getService();
            e4Service.getDataHandler().addStatusListener(MainActivity.this);
            updateServerStatus(e4Service.getDataHandler().getStatus());
            e4Service.getDataHandler().checkConnection();
            e4Service.addStatusListener(MainActivity.this);
            if (e4Service.isRecording()) {
                deviceStatusUpdated(null, E4DeviceStatusListener.Status.READY);
            }
            for (E4DeviceManager device : e4Service.getDevices()) {
                deviceStatusUpdated(device, E4DeviceStatusListener.Status.CONNECTED);
            }
            mBound = true;

            Button showButton = (Button) findViewById(R.id.showButton);
            showButton.setVisibility(View.VISIBLE);
            showButton.setOnClickListener(new View.OnClickListener() {
                final DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                final LinkedList<MeasurementTable.Measurement> reversedMeasurements = new LinkedList<>();
                final MeasurementTable table = e4Service.getDataHandler().getTable(topics.getInterBeatIntervalTopic());

                public void onClick(View v) {
                    reversedMeasurements.clear();
                    try (MeasurementIterator measurements = table.getMeasurements(25)) {
                        for (MeasurementTable.Measurement measurement : measurements) {
                            reversedMeasurements.addFirst(measurement);
                        }
                    }

                    if (!reversedMeasurements.isEmpty()) {
                        StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                        for (MeasurementTable.Measurement measurement : reversedMeasurements) {
                            sb.append(timeFormat.format(1000d * (Double)measurement.value.get(0)));
                            sb.append(": ");
                            sb.append(String.valueOf((int)(60d/((Number)measurement.value.get(2)).doubleValue())));
                            sb.append('\n');
                        }
                        String view = sb.toString();
                        Toast.makeText(MainActivity.this, view, Toast.LENGTH_LONG).show();
                        logger.info("Data:\n{}", view);
                    } else {
                        Toast.makeText(MainActivity.this, "No heart rate collected yet.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBound = false;
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
        serverStatusLabel = (TextView) findViewById(R.id.serverStatus);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);
        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setVisibility(View.INVISIBLE);
        reconnectButton = (Button) findViewById(R.id.reconnectButton);
        reconnectButton.setVisibility(View.INVISIBLE);

        reconnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mBound) {
                    e4Service.getDataHandler().checkConnection();
                }
            }
        });

        try {
            topics = E4Topics.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);
        mHandler = new Handler();
        mUIUpdater = new Runnable() {
            final DecimalFormat singleDecimal = new DecimalFormat("0.0");
            final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
            final DecimalFormat noDecimals = new DecimalFormat("0");
            @Override
            public void run() {
                if (!mBound) {
                    return;
                }
                Iterator<E4DeviceManager> devices = e4Service.getDevices().iterator();
                if (devices.hasNext()) {
                    E4DeviceManager e4DeviceManager = devices.next();
                    float[] acceleration = e4DeviceManager.getLatestAcceleration();
                    setText(accel_xLabel, acceleration[0], "g");
                    setText(accel_yLabel, acceleration[1], "g");
                    setText(accel_zLabel, acceleration[2], "g");
                    setText(bvpLabel, e4DeviceManager.getLatestBloodVolumePulse(), "\u00B5W");
                    setText(edaLabel, e4DeviceManager.getLatestElectroDermalActivity(), "\u00B5S");
                    setText(ibiLabel, e4DeviceManager.getLatestInterBeatInterval(), "s");
                    setText(temperatureLabel, e4DeviceManager.getLatestTemperature(), "\u2103");
                    setText(batteryLabel, 100*e4DeviceManager.getLatestBatteryLevel(), "%");
                }
            }

            void setText(TextView label, float value, String suffix) {
                if (Float.isNaN(value)) {
                    // em dash
                    label.setText("\u2014");
                } else {
                    label.setText(singleDecimal.format(value) + " " + suffix);
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
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                waitingForPermission = true;
                break;
            }
        }
        if (waitingForPermission) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_ENABLE_PERMISSIONS);
        } else {
            enableEmpatica();
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
        this.waitingForBind = true;
        bindToEmpatica();
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.waitingForBind = false;
        if (mBound) {
            e4Service.getDataHandler().removeStatusListener(this);
            e4Service.removeStatusListener(this);
            unbindService(mConnection);
            mBound = false;
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
                this.bindToEmpatica();
            } else {
                // User refused to grant permission.
                updateLabel(statusLabel, "Cannot connect to Empatica E4DeviceManager without location permissions");
            }
        }
    }

    private void enableEmpatica() {
        if (!waitingForPermission) {
            logger.info("Intending to start E4 service");
            Intent e4serviceIntent = new Intent(this, E4Service.class);
            startService(e4serviceIntent);
        }
    }

    private void bindToEmpatica() {
        if (waitingForBind && !waitingForPermission) {
            logger.info("Intending to bind to E4 service");
            Intent intent = new Intent(this, E4Service.class);
            bindService(intent, mConnection, Context.BIND_ABOVE_CLIENT);
            waitingForBind = false;
        }
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

    @Override
    public void updateServerStatus(final ServerStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case INACTIVE:
                        serverStatusLabel.setVisibility(View.INVISIBLE);
                        reconnectButton.setVisibility(View.INVISIBLE);
                        break;
                    case CONNECTED:
                        serverStatusLabel.setText("Server connected");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        reconnectButton.setVisibility(View.INVISIBLE);
                        break;
                    case DISCONNECTED:
                        serverStatusLabel.setText("Server disconnected");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        reconnectButton.setVisibility(View.VISIBLE);
                        break;
                    case CONNECTING:
                        serverStatusLabel.setText("Connecting to server");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        reconnectButton.setVisibility(View.INVISIBLE);
                        break;
                    case UPLOADING:
                        serverStatusLabel.setText("Uploading");
                        serverStatusLabel.setVisibility(View.VISIBLE);
                        reconnectButton.setVisibility(View.INVISIBLE);
                        break;
                }
            }
        });
    }

    @Override
    public void deviceStatusUpdated(final E4DeviceManager deviceManager, final E4DeviceStatusListener.Status status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String deviceName = deviceManager != null ? deviceManager.getDeviceName() : null;
                switch (status) {
                    case CONNECTED:
                        updateLabel(stopButton, "Stop Recording");
                        dataCnt.setVisibility(View.VISIBLE);
                        statusLabel.setText("CONNECTED");
                        deviceNameLabel.setText(deviceName);
                        break;
                    case CONNECTING:
                        updateLabel(stopButton, "Stop Recording");
                        statusLabel.setText("CONNECTING");
                        if (deviceName == null) {
                            deviceNameLabel.setText("\u2014");
                        } else {
                            deviceNameLabel.setText(deviceName);
                        }
                        break;
                    case DISCONNECTED:
                        dataCnt.setVisibility(View.INVISIBLE);
                        statusLabel.setText("DISCONNECTED");
                        deviceNameLabel.setText("\u2014");
                        updateLabel(stopButton, "Record");
                        break;
                    case READY:
                        statusLabel.setText("Scanning...");
                        deviceNameLabel.setText("\u2014");
                        stopButton.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                if (e4Service.isRecording()) {
                                    e4Service.disconnect();
                                } else {
                                    e4Service.connect();
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

    @Override
    public void deviceFailedToConnect(String name) {
        Toast.makeText(this, "Cannot connect to device " + name, Toast.LENGTH_SHORT).show();
    }
}