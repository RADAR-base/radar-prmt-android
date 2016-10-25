package org.radarcns.empaticaE4;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.SchemaRegistryRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class E4Service extends Service implements E4DeviceStatusListener {
    private final static int ONGOING_NOTIFICATION_ID = 11;
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON && !forcedDisconnect.get() && deviceScanner == null) {
                    logger.info("Bluetooth has turned on");
                    connect();
                } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    logger.warn("Bluetooth is turning off");
                    boolean oldDisconnect = forcedDisconnect.get();
                    disconnect();
                    forcedDisconnect.set(oldDisconnect);
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    logger.warn("Bluetooth is off");
                    enableBt();
                }
            }
        }
    };

    private final static Logger logger = LoggerFactory.getLogger(E4Service.class);
    private TableDataHandler dataHandler;
    private Collection<E4DeviceManager> devices;
    private E4DeviceManager deviceScanner;
    private E4Topics topics;
    private final AtomicBoolean forcedDisconnect = new AtomicBoolean(false);
    private final LocalBinder mBinder = new LocalBinder();
    private final Collection<E4DeviceStatusListener> listeners = new ArrayList<>();
    private final AtomicInteger numberOfActivitiesBound = new AtomicInteger(0);
    private String apiKey;
    private String groupId;
    private boolean isInForeground;

    @Override
    public void onCreate() {
        logger.info("Creating E4 service {}", this);

        super.onCreate();
        try {
            topics = E4Topics.getInstance();
        } catch (IOException ex) {
            logger.error("missing topic schema", ex);
            throw new RuntimeException(ex);
        }

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        enableBt();

        synchronized (this) {
            forcedDisconnect.set(false);
            numberOfActivitiesBound.set(0);
            listeners.clear();
            isInForeground = false;
            devices = new ArrayList<>();
            deviceScanner = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister broadcast listeners
        unregisterReceiver(mBluetoothReceiver);
        disconnect();
        try {
            dataHandler.close();
        } catch (IOException e) {
            // do nothing
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        onRebind(intent);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        if (numberOfActivitiesBound.getAndIncrement() == 0) {
            if (!forcedDisconnect.get() && deviceScanner == null && BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                connect();
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (numberOfActivitiesBound.decrementAndGet() == 0) {
            if (devices.isEmpty()) {
                stopSelf();
            }
        }
        return true;
    }

    public TableDataHandler getDataHandler() {
        return dataHandler;
    }

    public synchronized void startBackgroundListener(Notification notification) {
        if (!isInForeground) {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
            isInForeground = true;
        }
    }

    public synchronized void stopBackgroundListener() {
        if (isInForeground) {
            stopForeground(true);
            isInForeground = false;
        }
    }

    @Override
    public synchronized void deviceStatusUpdated(E4DeviceManager e4DeviceManager, Status status) {
        switch (status) {
            case CONNECTED:
                if (e4DeviceManager != deviceScanner) {
                    return;
                }
                devices.add(deviceScanner);
                deviceScanner = null;

                for (E4DeviceStatusListener listener : listeners) {
                    listener.deviceStatusUpdated(e4DeviceManager, E4DeviceStatusListener.Status.CONNECTED);
                }

                startScanning();
                break;
            case DISCONNECTED:
                if (e4DeviceManager == null) {
                    return;
                }
                if (devices.contains(e4DeviceManager)) {
                    devices.remove(e4DeviceManager);
                    if (!e4DeviceManager.isClosed()) {
                        e4DeviceManager.close();
                    }
                    for (E4DeviceStatusListener listener : listeners) {
                        listener.deviceStatusUpdated(e4DeviceManager, E4DeviceStatusListener.Status.DISCONNECTED);
                    }
                    if (devices.isEmpty()) {
                        stopBackgroundListener();
                        if (this.numberOfActivitiesBound.get() == 0) {
                            stopSelf();
                        }
                    }
                }
                break;
            case CONNECTING:
                for (E4DeviceStatusListener listener : listeners) {
                    listener.deviceStatusUpdated(e4DeviceManager, E4DeviceStatusListener.Status.CONNECTING);
                }
                break;
        }
    }

    public synchronized void disconnect() {
        forcedDisconnect.set(true);
        if (deviceScanner != null) {
            deviceScanner.close();
            deviceScanner = null;
        }
        for (E4DeviceManager device : devices) {
            deviceStatusUpdated(device, E4DeviceStatusListener.Status.DISCONNECTED);
        }
        for (E4DeviceStatusListener listener : listeners) {
            listener.deviceStatusUpdated(null, E4DeviceStatusListener.Status.DISCONNECTED);
        }
        if (dataHandler.isStarted()) {
            dataHandler.stop();
        }
    }

    public synchronized boolean isRecording() {
        return deviceScanner != null || !devices.isEmpty();
    }

    synchronized void connect() {
        if (deviceScanner != null) {
            throw new IllegalStateException("Already connecting");
        }
        if (!dataHandler.isStarted()) {
            dataHandler.start();
        }
        forcedDisconnect.set(false);
        startScanning();
    }

    private synchronized void startScanning() {
        // Only scan if no devices are connected.
        // TODO: support multiple devices
        if (deviceScanner != null) {
            throw new IllegalStateException("Already connecting");
        }
        deviceScanner = new E4DeviceManager(this, this, apiKey, groupId, dataHandler, topics);
        deviceScanner.start();
        for (E4DeviceStatusListener listener : listeners) {
            listener.deviceStatusUpdated(null, E4DeviceStatusListener.Status.READY);
        }
    }

    @Override
    public synchronized void deviceFailedToConnect(String deviceName) {
        for (E4DeviceStatusListener listener : listeners) {
            listener.deviceFailedToConnect(deviceName);
        }
    }

    public synchronized void addStatusListener(E4DeviceStatusListener statusListener) {
        this.listeners.add(statusListener);
    }

    public synchronized void removeStatusListener(E4DeviceStatusListener statusListener) {
        this.listeners.add(statusListener);
    }

    class LocalBinder extends Binder {
        E4Service getService() {
            return E4Service.this;
        }
    }

    synchronized Collection<E4DeviceManager> getDevices() {
        return new ArrayList<>(devices);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("Starting E4 service {}", this);
        synchronized (this) {
            if (dataHandler == null) {
                apiKey = intent.getStringExtra("empatica_api_key");
                groupId = intent.getStringExtra("group_id");
                URL kafkaUrl = null;
                SchemaRetriever remoteSchemaRetriever = null;
                if (intent.hasExtra("kafka_rest_proxy_url")) {
                    String kafkaUrlString = intent.getStringExtra("kafka_rest_proxy_url");
                    if (!kafkaUrlString.isEmpty()) {
                        remoteSchemaRetriever = new SchemaRegistryRetriever(intent.getStringExtra("schema_registry_url"));
                        try {
                            kafkaUrl = new URL(kafkaUrlString);
                        } catch (MalformedURLException e) {
                            logger.error("Malformed Kafka server URL {}", kafkaUrlString);
                            throw new RuntimeException(e);
                        }
                    }
                }
                long dataRetentionMs = intent.getLongExtra("data_retention_ms", 86400000);
                dataHandler = new TableDataHandler(getApplicationContext(), 2500, kafkaUrl, remoteSchemaRetriever,
                        dataRetentionMs, topics.getAccelerationTopic(),
                        topics.getBloodVolumePulseTopic(), topics.getElectroDermalActivityTopic(),
                        topics.getInterBeatIntervalTopic(), topics.getTemperatureTopic());
            }
        }
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    void enableBt() {
        BluetoothAdapter btAdaptor = BluetoothAdapter.getDefaultAdapter();
        if (!btAdaptor.isEnabled() && btAdaptor.getState() != BluetoothAdapter.STATE_TURNING_ON) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(btIntent);
        }
    }
}
