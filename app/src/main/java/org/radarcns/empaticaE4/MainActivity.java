package org.radarcns.empaticaE4;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.android.MeasurementIterator;
import org.radarcns.android.MeasurementTable;
import org.radarcns.data.Record;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
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
    private Button reconnectButton;
    private RelativeLayout dataCnt;
    private RelativeLayout deviceView;
    private Map<String, Button> deviceButtons;

    private long uiRefreshRate;
    private Handler mHandler;
    private Runnable mUIScheduler;
    private Runnable mUIUpdater;
    private E4Topics topics;
    private E4Service e4Service;
    private boolean mBound;
    private boolean waitingForPermission;
    private boolean waitingForBind;
    private Collection<E4DeviceManager> devices;
    private E4DeviceManager activeDevice = null;

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
                for (E4DeviceManager device : e4Service.getDevices()) {
                    deviceStatusUpdated(device, device.getStatus());
                }
            }
            for (E4DeviceManager device : e4Service.getDevices()) {
                deviceStatusUpdated(device, E4DeviceStatusListener.Status.CONNECTED);
            }
            mBound = true;

            Button showButton = (Button) findViewById(R.id.showButton);
            showButton.setVisibility(View.VISIBLE);
            showButton.setOnClickListener(new View.OnClickListener() {
                final DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                final LinkedList<Record<String, GenericRecord>> reversedMeasurements = new LinkedList<>();
                final MeasurementTable table = e4Service.getDataHandler().getCache(topics.getInterBeatIntervalTopic());

                public void onClick(View v) {
                    reversedMeasurements.clear();
                    try (MeasurementIterator measurements = table.getMeasurements(25)) {
                        for (Record<String, GenericRecord> measurement : measurements) {
                            reversedMeasurements.addFirst(measurement);
                        }
                    }

                    if (!reversedMeasurements.isEmpty()) {
                        StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                        for (Record<String, GenericRecord> measurement : reversedMeasurements) {
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

        this.devices = new ArrayList<>();

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
        deviceView = (RelativeLayout) findViewById(R.id.deviceNames);
        emptyDevices = (TextView) findViewById(R.id.emptyDevices);
        serverStatusLabel = (TextView) findViewById(R.id.serverStatus);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceButtons = new HashMap<>();
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
                if (!mBound || activeDevice == null) {
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
            e4serviceIntent.putExtra("kafka_rest_proxy_url", getString(R.string.kafka_rest_proxy_url));
            e4serviceIntent.putExtra("schema_registry_url", getString(R.string.schema_registry_url));
            e4serviceIntent.putExtra("group_id", getString(R.string.group_id));
            e4serviceIntent.putExtra("empatica_api_key", getString(R.string.apikey));
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
                    case READY:
                    case DISABLED:
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
                switch (status) {
                    case CONNECTED:
                        if (devices.isEmpty()) {
                            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
                            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                            Notification.Builder notificationBuilder = new Notification.Builder(getApplicationContext());
                            Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                                    R.mipmap.ic_launcher);
                            notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
                            notificationBuilder.setLargeIcon(largeIcon);
                            notificationBuilder.setTicker(getText(R.string.service_notification_ticker));
                            notificationBuilder.setWhen(System.currentTimeMillis());
                            notificationBuilder.setContentIntent(pendingIntent);
                            notificationBuilder.setContentText(getText(R.string.service_notification_text));
                            notificationBuilder.setContentTitle(getText(R.string.service_notification_title));
                            Notification notification = notificationBuilder.build();
                            e4Service.startBackgroundListener(notification);
                        }
                        devices.add(deviceManager);
                        addDeviceButton(deviceManager);
                        if (activeDevice == null) {
                            activeDevice = deviceManager;
                        }

                        updateLabel(stopButton, "Stop Recording");
                        dataCnt.setVisibility(View.VISIBLE);
                        statusLabel.setText("CONNECTED");
                        break;
                    case CONNECTING:
                        updateLabel(stopButton, "Stop Recording");
                        statusLabel.setText("CONNECTING");
                        break;
                    case DISCONNECTED:
                        if (deviceManager != null) {
                            Button btn = deviceButtons.remove(deviceManager.getDeviceName());
                            deviceView.removeView(btn);
                            devices.remove(deviceManager);
                            if (deviceManager.equals(activeDevice)) {
                                activeDevice = null;
                            }
                        } else {
                            for (Button btn : deviceButtons.values()) {
                                deviceView.removeView(btn);
                            }
                            deviceButtons.clear();
                            devices.clear();
                            devices.addAll(e4Service.getDevices());
                            for (E4DeviceManager device : devices) {
                                addDeviceButton(device);
                            }
                            if (activeDevice != null && !devices.contains(activeDevice)) {
                                activeDevice = null;
                            }
                        }
                        if (devices.isEmpty()) {
                            emptyDevices.setVisibility(View.VISIBLE);
                        }
                        dataCnt.setVisibility(View.INVISIBLE);
                        statusLabel.setText("DISCONNECTED");
                        e4Service.stopBackgroundListener();
                        updateLabel(stopButton, "Record");
                        break;
                    case READY:
                        statusLabel.setText("Scanning...");
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
            private void addDeviceButton(final E4DeviceManager manager) {
                String name = manager.getDeviceName();
                if (name != null) {
                    emptyDevices.setVisibility(View.INVISIBLE);
                    Button btn = new Button(MainActivity.this);
                    btn.setLayoutParams(new LayoutParams(
                            LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
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
        });
    }

    @Override
    public void deviceFailedToConnect(String name) {
        Toast.makeText(this, "Cannot connect to device " + name, Toast.LENGTH_SHORT).show();
    }
}