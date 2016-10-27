package org.radarcns.empaticaE4;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.TableDataHandler;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class E4Service extends Service implements E4DeviceStatusListener, ServerStatusListener {
    private final static int ONGOING_NOTIFICATION_ID = 11;
    public final static int TRANSACT_GET_RECORDS = 12;
    public final static int TRANSACT_GET_DEVICE_STATUS = 13;
    public final static String SERVER_STATUS_CHANGED = "org.radarcns.android.ServerStatusListener.Status";
    public final static String DEVICE_STATUS_SERVICE_CLASS = "org.radarcns.empaticaE4.E4Service.getClass";
    public final static String DEVICE_STATUS_CHANGED = "org.radarcns.empaticaE4.E4DeviceStatusListener.Status";
    public final static String DEVICE_STATUS_NAME = "org.radarcns.empaticaE4.E4DeviceManager.getDeviceName";
    public final static String DEVICE_CONNECT_FAILED = "org.radarcns.empaticaE4.E4DeviceStatusListener.deviceFailedToConnect";

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    logger.warn("Bluetooth is off");
                    stopSelf();
                }
            }
        }
    };

    private final static Logger logger = LoggerFactory.getLogger(E4Service.class);
    private TableDataHandler dataHandler;
    private E4DeviceManager deviceScanner;
    private E4Topics topics;
    private final LocalBinder mBinder = new LocalBinder();
    private final AtomicInteger numberOfActivitiesBound = new AtomicInteger(0);
    private String apiKey;
    private String groupId;
    private boolean isInForeground;
    private boolean isConnected;

    @Override
    public void onCreate() {
        logger.info("Creating E4 service {}", this);

        super.onCreate();
        topics = E4Topics.getInstance();

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            logger.warn("Bluetooth disabled. Cannot listen for devices.");
            stopSelf();
        }
        synchronized (this) {
            numberOfActivitiesBound.set(0);
            isInForeground = false;
            deviceScanner = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister broadcast listeners
        unregisterReceiver(mBluetoothReceiver);
        Intent statusChanged = new Intent(DEVICE_STATUS_CHANGED);
        statusChanged.putExtra(DEVICE_STATUS_CHANGED, E4DeviceStatusListener.Status.DISCONNECTED.ordinal());
        statusChanged.putExtra(DEVICE_STATUS_SERVICE_CLASS, getClass().getName());
        if (deviceScanner != null && deviceScanner.getDeviceName() != null) {
            statusChanged.putExtra(DEVICE_STATUS_NAME, deviceScanner.getDeviceName());
        }
        sendBroadcast(statusChanged);

        if (dataHandler.isStarted()) {
            dataHandler.stop();
        }
        try {
            dataHandler.close();
        } catch (IOException e) {
            // do nothing
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("Starting E4 service {}", this);
        ensureDataHandler(intent);
        dataHandler.start();
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        onRebind(intent);
        return mBinder;
    }

    @Override
    public synchronized void onRebind(Intent intent) {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            stopSelf();
        }
        ensureDataHandler(intent);
        if (numberOfActivitiesBound.getAndIncrement() == 0) {
            if (!isConnected) {
                startScanning();
            }
        }
    }

    @Override
    public synchronized boolean onUnbind(Intent intent) {
        if (numberOfActivitiesBound.decrementAndGet() == 0) {
            if (!isConnected) {
                stopSelf();
            }
        }
        return true;
    }

    public synchronized void startBackgroundListener() {
        if (!isInForeground) {
            Context context = getApplicationContext();
            Intent notificationIntent = new Intent(context, E4Service.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

            Notification.Builder notificationBuilder = new Notification.Builder(getApplicationContext());
            Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
            notificationBuilder.setLargeIcon(largeIcon);
            notificationBuilder.setTicker(getText(R.string.service_notification_ticker));
            notificationBuilder.setWhen(System.currentTimeMillis());
            notificationBuilder.setContentIntent(pendingIntent);
            notificationBuilder.setContentText(getText(R.string.service_notification_text));
            notificationBuilder.setContentTitle(getText(R.string.service_notification_title));
            Notification notification = notificationBuilder.build();

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
    public synchronized void deviceStatusUpdated(E4DeviceManager e4DeviceManager, E4DeviceStatusListener.Status status) {
        if (e4DeviceManager != deviceScanner) {
            return;
        }

        Intent statusChanged = new Intent(DEVICE_STATUS_CHANGED);
        statusChanged.putExtra(DEVICE_STATUS_CHANGED, status.ordinal());
        statusChanged.putExtra(DEVICE_STATUS_SERVICE_CLASS, getClass().getName());
        if (e4DeviceManager != null && e4DeviceManager.getDeviceName() != null) {
            statusChanged.putExtra(DEVICE_STATUS_NAME, e4DeviceManager.getDeviceName());
        }
        sendBroadcast(statusChanged);

        switch (status) {
            case CONNECTED:
                isConnected = true;
                startBackgroundListener();
                break;
            case DISCONNECTED:
                deviceScanner = null;
                stopBackgroundListener();
                if (e4DeviceManager != null && !e4DeviceManager.isClosed()) {
                    e4DeviceManager.close();
                }
                if (this.numberOfActivitiesBound.get() == 0) {
                    stopSelf();
                } else {
                    startScanning();
                }
                break;
        }
    }

    private synchronized void startScanning() {
        // Only scan if no devices are connected.
        if (deviceScanner != null) {
            throw new IllegalStateException("Already connecting");
        }
        deviceScanner = new E4DeviceManager(this, this, apiKey, groupId, dataHandler, topics);
        deviceScanner.start();
    }

    @Override
    public synchronized void deviceFailedToConnect(String deviceName) {
        Intent statusChanged = new Intent(DEVICE_CONNECT_FAILED);
        statusChanged.putExtra(DEVICE_STATUS_SERVICE_CLASS, getClass().getName());
        statusChanged.putExtra(DEVICE_STATUS_NAME, deviceName);
        sendBroadcast(statusChanged);
    }

    @Override
    public void updateServerStatus(ServerStatusListener.Status status) {
        Intent statusIntent = new Intent(SERVER_STATUS_CHANGED);
        statusIntent.putExtra(SERVER_STATUS_CHANGED, status.ordinal());
        sendBroadcast(statusIntent);
    }

    class LocalBinder extends Binder {
        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            try {
                switch (code) {
                    case TRANSACT_GET_RECORDS:
                        AvroTopic topic = topics.getTopic(data.readString());
                        int limit = data.readInt();
                        AvroEncoder.AvroWriter<MeasurementKey> keyWriter = new SpecificRecordEncoder<MeasurementKey>(true).writer(topic.getKeySchema());
                        AvroEncoder.AvroWriter<SpecificRecord> valueWriter = new SpecificRecordEncoder<>(true).writer(topic.getKeySchema());

                        List<Record<MeasurementKey, SpecificRecord>> records = dataHandler.getCache(topic).getMeasurements(limit);
                        reply.writeInt(records.size());
                        for (Record<MeasurementKey, SpecificRecord> record : records) {
                            reply.writeLong(record.offset);
                            reply.writeByteArray(keyWriter.encode(record.key));
                            reply.writeByteArray(valueWriter.encode(record.value));
                        }
                        break;
                    case TRANSACT_GET_DEVICE_STATUS:
                        E4DeviceStatus status;
                        if (deviceScanner == null) {
                            status = new E4DeviceStatus();
                            status.setStatus(E4DeviceStatusListener.Status.DISCONNECTED);
                        } else {
                            status = deviceScanner.getStatus();
                        }
                        status.writeToParcel(reply, 0);
                    default:
                        return false;
                }
                return true;
            } catch (IOException e) {
                throw new RemoteException("IOException: " + e.getMessage());
            }
        }
    }

    synchronized void ensureDataHandler(Intent intent) {
        if (dataHandler == null) {
            apiKey = intent.getStringExtra("empatica_api_key");
            groupId = intent.getStringExtra("group_id");
            URL kafkaUrl = null;
            SchemaRetriever remoteSchemaRetriever = null;
            if (intent.hasExtra("kafka_rest_proxy_url")) {
                String kafkaUrlString = intent.getStringExtra("kafka_rest_proxy_url");
                if (!kafkaUrlString.isEmpty()) {
                    remoteSchemaRetriever = new SchemaRetriever(intent.getStringExtra("schema_registry_url"));
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
}
