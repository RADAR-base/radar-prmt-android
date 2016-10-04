package org.radarcns.empaticaE4;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
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
import org.radarcns.collect.Topic;
import org.radarcns.collect.rest.RestProducer;
import org.radarcns.collect.rest.SchemaRegistryRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {
    private final static Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long STREAMING_TIME = 10000; // Stops streaming 10 seconds after connection (was 10 sec)
    private CountdownTimer mTimer;

    private EmpaDeviceManager deviceManager;
    private BluetoothDevice mLastDeviceConnected;

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
    private TextView timerLabel;
    private Button restartButton;
    private Button stopButton;
    private Button showButton;
    private RelativeLayout dataCnt;

    private MeasurementTable bvpTable;
    private MeasurementTable accTable;
    private MeasurementTable edaTable;
    private MeasurementTable tempTable;
    private MeasurementTable ibiTable;
    private Topic accelerationTopic;
    private Topic batteryTopic;
    private Topic bvpTopic;
    private Topic edaTopic;
    private Topic ibiTopic;
    private Topic temperatureTopic;

    private RestProducer sender;
    private String groupId;
    private String deviceId;

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
        timerLabel = (TextView) findViewById(R.id.timer);
        restartButton = (Button) findViewById(R.id.restartButton);
        showButton = (Button) findViewById(R.id.showButton);
        stopButton = (Button) findViewById(R.id.stopButton);

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

        SchemaRetriever remoteSchemaRetriever = new SchemaRegistryRetriever("http://radar-test.thehyve.net:8081");
        sender = new RestProducer("radar-test.thehyve.net:8082", 1000, remoteSchemaRetriever);
        sender.start();

        bvpTable = new MeasurementTable(context, bvpTopic);
        accTable = new MeasurementTable(context, accelerationTopic);
        edaTable = new MeasurementTable(context, edaTopic);
        tempTable = new MeasurementTable(context, temperatureTopic);
        ibiTable = new MeasurementTable(context, ibiTopic);

        restartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "RESTARTED RECORDING", Toast.LENGTH_SHORT).show();
                deviceManager.startScanning();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                deviceManager.disconnect();
            }
        });

        showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String view = "";
                DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                try (MeasurementIterator measurements = ibiTable.getMeasurements(100)) {
                    for (Pair<String, GenericRecord> measurement : measurements) {
                        String t = timeFormat.format(1000d * (Double)measurement.second.get(0));
                        view = t + ": " + measurement.second.get(2) + "\n" + view;
                    }
                }
                Toast.makeText(MainActivity.this, view, Toast.LENGTH_LONG).show();
            }
        });

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        String empatica_api_key = getString(R.string.apikey);
        groupId = getString(R.string.group_id);
        deviceId = null;
        deviceManager.authenticateWithAPIKey(empatica_api_key);
        uploadTables();

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate
                (new Runnable() {
                    public void run() {
                        updateTables();
                    }
                }, 5, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate
                (new Runnable() {
                    public void run() {
                        cleanTables();
                    }
                }, 0, 1, TimeUnit.HOURS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            sender.flush();
        } catch (InterruptedException e) {
            // do nothing
        }
        deviceManager.stopScanning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            sender.close();
        } catch (InterruptedException e) {
            // do nothing
        }
        cleanTables();
        deviceManager.cleanUp();
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
                mLastDeviceConnected = bluetoothDevice;
                this.deviceId = this.groupId + "-" + bluetoothDevice.getAddress();
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you cannott connect to this device", Toast.LENGTH_SHORT).show();
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
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
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
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
        // The device manager has established a connection
        } else if (status == EmpaStatus.CONNECTED) {
            // Stop streaming after STREAMING_TIME
            startStreaming(STREAMING_TIME);
//            dataCnt.setVisibility(View.VISIBLE);
//            mTimer = new CountdownTimer(STREAMING_TIME);
        // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
        }
    }

    private void startStreaming(final long streaming_time) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                dataCnt.setVisibility(View.VISIBLE);
                // After a time period the run() is executed, disconnecting the device.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Disconnect device
                        deviceManager.disconnect();
                    }
                }, streaming_time);
            }
        });

        // Start the clock
        mTimer = new CountdownTimer(STREAMING_TIME);
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, String.valueOf(x));
        updateLabel(accel_yLabel, String.valueOf(y));
        updateLabel(accel_zLabel, String.valueOf(z));

        sendAndAddToTable(accelerationTopic, accTable, timestamp, x, y, z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, bvp + "   " + timestamp);

        sendAndAddToTable(bvpTopic, bvpTable, timestamp, bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format(Locale.ENGLISH, "%.1f %%", battery * 100));

        GenericRecord record = batteryTopic.createSimpleRecord(timestamp, battery);
        sender.send(batteryTopic.getName(), deviceId, record);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, String.valueOf(gsr));

        sendAndAddToTable(edaTopic, edaTable, timestamp, gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, String.valueOf(ibi));

        sendAndAddToTable(ibiTopic, ibiTable, timestamp, ibi);
    }

    @Override
    public void didReceiveTemperature(float temperature, double timestamp) {
        updateLabel(temperatureLabel, String.valueOf(temperature));

        sendAndAddToTable(temperatureTopic, tempTable, timestamp, temperature);
        // Update timer label
        updateLabel(timerLabel, String.valueOf(mTimer.getRemainingSeconds()));
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

    private void sendAndAddToTable(Topic topic, MeasurementTable table, double timestamp, Object... values) {
        GenericRecord record = topic.createSimpleRecord(timestamp, values);
        long offset = sender.send(topic.getName(), deviceId, record);
        table.addMeasurement(offset, deviceId, timestamp, record.get("timeReceived"), values);
    }

    private void cleanTables() {
        double timestamp = (System.currentTimeMillis() - Long.parseLong(getString(R.string.data_retention_ms))) / 1000d;
        bvpTable.removeBeforeTimestamp(timestamp);
        accTable.removeBeforeTimestamp(timestamp);
        edaTable.removeBeforeTimestamp(timestamp);
        tempTable.removeBeforeTimestamp(timestamp);
        ibiTable.removeBeforeTimestamp(timestamp);
    }

    private void updateTables() {
        bvpTable.markSent(sender.getLastSentOffset(bvpTopic.getName()));
        accTable.markSent(sender.getLastSentOffset(accelerationTopic.getName()));
        edaTable.markSent(sender.getLastSentOffset(edaTopic.getName()));
        tempTable.markSent(sender.getLastSentOffset(temperatureTopic.getName()));
        ibiTable.markSent(sender.getLastSentOffset(ibiTopic.getName()));
    }

    private void uploadTables() {
        uploadTable(bvpTopic, bvpTable);
        uploadTable(accelerationTopic, accTable);
        uploadTable(edaTopic, edaTable);
        uploadTable(temperatureTopic, tempTable);
        uploadTable(ibiTopic, ibiTable);
    }

    private void uploadTable(Topic topic, MeasurementTable table) {
        try (MeasurementIterator measurements = table.getUnsentMeasurements()) {
            for (Pair<String, GenericRecord> record : measurements) {
                sender.send(topic.getName(), record.first, record.second);
            }
        }
    }
}