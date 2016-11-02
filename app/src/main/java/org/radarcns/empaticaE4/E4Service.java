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
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.SchemaRetriever;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class E4Service extends Service implements DeviceStatusListener, ServerStatusListener {
    private final static int ONGOING_NOTIFICATION_ID = 11;
    public final static int TRANSACT_GET_RECORDS = 12;
    public final static int TRANSACT_GET_DEVICE_STATUS = 13;
    public final static int TRANSACT_START_RECORDING = 14;
    public final static int TRANSACT_STOP_RECORDING = 15;
    public final static int TRANSACT_GET_SERVER_STATUS = 16;
    public final static String SERVER_STATUS_CHANGED = "org.radarcns.android.ServerStatusListener.Status";
    public final static String DEVICE_STATUS_SERVICE_CLASS = "org.radarcns.empaticaE4.E4Service.getClass";
    public final static String DEVICE_STATUS_CHANGED = "org.radarcns.empaticaE4.E4DeviceStatusListener.Status";
    public final static String DEVICE_STATUS_NAME = "org.radarcns.empaticaE4.E4DeviceManager.getName";
    public final static String DEVICE_CONNECT_FAILED = "org.radarcns.empaticaE4.E4DeviceStatusListener.deviceFailedToConnect";

    /** Stops the device when bluetooth is disabled. */
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF: case BluetoothAdapter.STATE_OFF:
                        logger.warn("Bluetooth is off");
                        if (deviceScanner != null) {
                            try {
                                deviceScanner.close();
                            } catch (IOException e) {
                                // do nothing
                            }
                            deviceScanner = null;
                        }
                        break;
                }
            }
        }
    };

    private final static Logger logger = LoggerFactory.getLogger(E4Service.class);
    private TableDataHandler dataHandler;
    private DeviceManager deviceScanner;
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

        if (deviceScanner != null) {
            try {
                deviceScanner.close();
            } catch (IOException e) {
                // do nothing
            }
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
        onRebind(intent);
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
            //noinspection unchecked
            dataHandler = new TableDataHandler(this, 2500, kafkaUrl, remoteSchemaRetriever,
                    dataRetentionMs, topics.getAccelerationTopic(),
                    topics.getBloodVolumePulseTopic(), topics.getElectroDermalActivityTopic(),
                    topics.getInterBeatIntervalTopic(), topics.getTemperatureTopic(),
                    topics.getSensorStatusTopic());
            dataHandler.addStatusListener(this);
            dataHandler.start();
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

    @Override
    public synchronized void deviceFailedToConnect(String deviceName) {
        Intent statusChanged = new Intent(DEVICE_CONNECT_FAILED);
        statusChanged.putExtra(DEVICE_STATUS_SERVICE_CLASS, getClass().getName());
        statusChanged.putExtra(DEVICE_STATUS_NAME, deviceName);
        sendBroadcast(statusChanged);
    }

    @Override
    public synchronized void deviceStatusUpdated(DeviceManager e4DeviceManager, DeviceStatusListener.Status status) {
        if (e4DeviceManager != deviceScanner) {
            return;
        }

        Intent statusChanged = new Intent(DEVICE_STATUS_CHANGED);
        statusChanged.putExtra(DEVICE_STATUS_CHANGED, status.ordinal());
        statusChanged.putExtra(DEVICE_STATUS_SERVICE_CLASS, getClass().getName());
        if (e4DeviceManager.getName() != null) {
            statusChanged.putExtra(DEVICE_STATUS_NAME, e4DeviceManager.getName());
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
                if (!e4DeviceManager.isClosed()) {
                    try {
                        e4DeviceManager.close();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
                if (this.numberOfActivitiesBound.get() == 0) {
                    stopSelf();
                }
                break;
        }
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
                        AvroTopic<MeasurementKey, ? extends SpecificRecord> topic = topics.getTopic(data.readString());
                        int limit = data.readInt();
                        dataHandler.getCache(topic).writeRecordsToParcel(reply, limit);
                        break;
                    case TRANSACT_GET_DEVICE_STATUS:
                        if (deviceScanner == null) {
                            E4DeviceStatus newStatus = new E4DeviceStatus();
                            newStatus.setStatus(DeviceStatusListener.Status.DISCONNECTED);
                            newStatus.writeToParcel(reply, 0);
                        } else {
                            deviceScanner.getState().writeToParcel(reply, 0);
                        }
                        break;
                    case TRANSACT_START_RECORDING:
                        if (deviceScanner == null) {
                            deviceScanner = new E4DeviceManager(E4Service.this, E4Service.this, apiKey, groupId, dataHandler, topics);
                            deviceScanner.start();
                        }
                        deviceScanner.getState().writeToParcel(reply, 0);
                        break;
                    case TRANSACT_STOP_RECORDING:
                        if (deviceScanner != null) {
                            if (!deviceScanner.isClosed()) {
                                deviceScanner.close();
                            }
                            deviceScanner = null;
                        }
                        break;
                    case TRANSACT_GET_SERVER_STATUS:
                        reply.writeInt(dataHandler.getStatus().ordinal());
                        break;
                    default:
                        return false;
                }
                return true;
            } catch (IOException e) {
                throw new RemoteException("IOException: " + e.getMessage());
            }
        }
    }
}
