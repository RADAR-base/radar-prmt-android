package org.radarcns.empaticaE4;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.apache.avro.generic.GenericRecord;
import org.radarcns.SchemaRetriever;
import org.radarcns.android.MeasurementIterator;
import org.radarcns.android.MeasurementTable;
import org.radarcns.collect.LocalSchemaRetriever;
import org.radarcns.collect.SchemaRegistryRetriever;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {
    private final static Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_COARSE_LOCATION = 2;
    private EmpaDeviceManager deviceManager;

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
    private Button restartButton;
    private Button stopButton;
    private RelativeLayout dataCnt;

    private Topic accelerationTopic;
    private Topic batteryTopic;
    private Topic bvpTopic;
    private Topic edaTopic;
    private Topic ibiTopic;
    private Topic temperatureTopic;

    private String groupId;
    private String deviceId;
    private long uiRefreshRate;
    private Map<TextView, Long> lastRefresh;
    private boolean deviceManagerIsReady;
    private DataHandler dataHandler;

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted.
                enableEmpatica();
            } else {
                // User refused to grant permission.
                updateLabel(statusLabel, "Cannot connect to Empatica E4 without location permissions");
            }
        }
    }

    private void enableEmpatica() {
        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        String empatica_api_key = getString(R.string.apikey);
        groupId = getString(R.string.group_id);
        deviceId = null;
        deviceManager.authenticateWithAPIKey(empatica_api_key);

        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                deviceManager.disconnect();
            }
        });
        stopButton.setVisibility(View.VISIBLE);
        restartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "RESTARTED RECORDING", Toast.LENGTH_SHORT).show();
                deviceManager.startScanning();
            }
        });
        restartButton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Context context = getApplicationContext();

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (RelativeLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);
        restartButton = (Button) findViewById(R.id.restartButton);
        restartButton.setVisibility(View.INVISIBLE);
        Button showButton = (Button) findViewById(R.id.showButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setVisibility(View.INVISIBLE);

        lastRefresh = new HashMap<>();

        SchemaRetriever localSchemaRetriever = new LocalSchemaRetriever();
        try {
            accelerationTopic = new Topic("empatica_e4_acceleration", localSchemaRetriever);
            batteryTopic = new Topic("empatica_e4_battery_level", localSchemaRetriever);
            bvpTopic = new Topic("empatica_e4_blood_volume_pulse", localSchemaRetriever);
            edaTopic = new Topic("empatica_e4_electrodermal_activity", localSchemaRetriever);
            ibiTopic = new Topic("empatica_e4_inter_beat_interval", localSchemaRetriever);
            temperatureTopic = new Topic("empatica_e4_temperature", localSchemaRetriever);
        } catch (IOException ex) {
            logger.error("missing topic schema", ex);
            throw new RuntimeException(ex);
        }

        SchemaRetriever remoteSchemaRetriever = new SchemaRegistryRetriever(getString(R.string.schema_registry_url));
        URL kafkaUrl;
        try {
            kafkaUrl = new URL(getString(R.string.kafka_rest_proxy_url));
        } catch (MalformedURLException e) {
            logger.error("Malformed Kafka server URL {}", getString(R.string.kafka_rest_proxy_url));
            throw new RuntimeException(e);
        }
        dataHandler = new DataHandler(context, 1000, kafkaUrl, 5000, 1000, remoteSchemaRetriever, Long.parseLong(getString(R.string.data_retention_ms)), accelerationTopic, bvpTopic, edaTopic, ibiTopic, temperatureTopic);

        showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String view = "";
                DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                try (MeasurementIterator measurements = dataHandler.getTable(ibiTopic).getMeasurements(100)) {
                    for (MeasurementTable.Measurement measurement : measurements) {
                        String t = timeFormat.format(1000d * (Double)measurement.value.get(0));
                        view = t + ": " + measurement.value.get(2) + "\n" + view;
                    }
                }
                logger.info("Data:\n{}", view);
                Toast.makeText(MainActivity.this, view, Toast.LENGTH_LONG).show();
            }
        });

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);

        deviceManagerIsReady = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_COARSE_LOCATION);
            deviceManager = null;
        } else {
            enableEmpatica();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (deviceManagerIsReady) {
            deviceManager.stopScanning();
        }
        try {
            dataHandler.pause();
        } catch (InterruptedException e) {
            logger.warn("Data handler interrupted");
        }
    }

    protected void onResume() {
        super.onResume();
        dataHandler.start();
        if (deviceManagerIsReady) {
            deviceManager.startScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            dataHandler.close();
        } catch (InterruptedException e) {
            // do nothing
        }
        if (deviceManagerIsReady) {
            deviceManager.cleanUp();
        }
    }

    @Override
    public void didDiscoverDevice(BluetoothDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                this.deviceId = this.groupId + "-" + bluetoothDevice.getAddress();
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you cannot connect to this device", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "Not allowed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                updateLabel(statusLabel, "Please enable Bluetooth for any incoming data");
                return;
            }
            if (resultCode == Activity.RESULT_OK) {
                deviceManager.startScanning();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus status, EmpaSensorType type) {
        // No need to implement this right now
//        if (status == EmpaSensorStatus.DEAD) {
//            // "Dead";
//        }
        Toast.makeText(MainActivity.this, "" + status, Toast.LENGTH_LONG).show();
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, "READY - Turn on your device");
            // Start scanning
            deviceManagerIsReady = true;
            deviceManager.startScanning();
        // The device manager has established a connection
        } else if (status == EmpaStatus.CONNECTED) {
            // Stop streaming after STREAMING_TIME
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataCnt.setVisibility(View.VISIBLE);
                }
            });
        // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "DISCONNECTED");
            if (deviceManagerIsReady) {
                deviceManager.startScanning();
            }
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, String.valueOf(x));
        updateLabel(accel_yLabel, String.valueOf(y));
        updateLabel(accel_zLabel, String.valueOf(z));
        dataHandler.sendAndAddToTable(accelerationTopic, deviceId, timestamp, "x", x / 64f, "y", y / 64f, "z", z / 64f);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, bvp + "   " + timestamp);
        dataHandler.sendAndAddToTable(bvpTopic, deviceId, timestamp, "bloodVolumePulse", bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format(Locale.ENGLISH, "%.1f %%", battery * 100));
        GenericRecord record = batteryTopic.createSimpleRecord(timestamp, "batteryLevel", battery);
        dataHandler.trySend(0L, batteryTopic.getName(), deviceId, record);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, String.valueOf(gsr));
        dataHandler.sendAndAddToTable(edaTopic, deviceId, timestamp, "electroDermalActivity", gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, String.valueOf(ibi));
        dataHandler.sendAndAddToTable(ibiTopic, deviceId, timestamp, "interBeatInterval", ibi);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        updateLabel(temperatureLabel, String.valueOf(temperature));
        dataHandler.sendAndAddToTable(temperatureTopic, deviceId, timestamp, "temperature", temperature);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        long now = System.currentTimeMillis();
        Long refresh = this.lastRefresh.get(label);
        if (refresh == null || now - refresh > uiRefreshRate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    label.setText(text);
                }
            });
            this.lastRefresh.put(label, now);
        }
    }
}