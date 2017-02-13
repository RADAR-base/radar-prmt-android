package org.radarcns.android;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import org.radarcns.MainActivity;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.radarcns.android.DeviceService.DEVICE_SERVICE_CLASS;
import static org.radarcns.android.DeviceService.SERVER_RECORDS_SENT_NUMBER;
import static org.radarcns.android.DeviceService.SERVER_RECORDS_SENT_TOPIC;
import static org.radarcns.android.DeviceService.SERVER_STATUS_CHANGED;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_CHANGED;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_NAME;

public class DeviceServiceConnection<S extends DeviceState> extends BaseServiceConnection<S> {
    private static final Logger logger = LoggerFactory.getLogger(DeviceServiceConnection.class);
    private final MainActivity mainActivity;
    private final String serviceClassName;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DEVICE_STATUS_CHANGED)) {
                if (getServiceClassName().equals(intent.getStringExtra(DEVICE_SERVICE_CLASS))) {
                    if (intent.hasExtra(DEVICE_STATUS_NAME)) {
                        deviceName = intent.getStringExtra(DEVICE_STATUS_NAME);
                        logger.info("Device status changed of device {}", deviceName);
                    }
                    setDeviceStatus(DeviceStatusListener.Status.values()[intent.getIntExtra(DEVICE_STATUS_CHANGED, 0)]);
                    logger.info("Updated device status to {}", getDeviceStatus());
                    mainActivity.deviceStatusUpdated(DeviceServiceConnection.this, getDeviceStatus());
                }
            }
        }
    };

    private final BroadcastReceiver serverStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intentMatches(intent, SERVER_STATUS_CHANGED)) {
                final ServerStatusListener.Status status = ServerStatusListener.Status.values()[intent.getIntExtra(SERVER_STATUS_CHANGED, 0)];
                mainActivity.updateServerStatus(DeviceServiceConnection.this, status);
            } else if (intentMatches(intent, SERVER_RECORDS_SENT_TOPIC)) {
                String topic = intent.getStringExtra(SERVER_RECORDS_SENT_TOPIC); // topicName that updated
                int numberOfRecordsSent = intent.getIntExtra(SERVER_RECORDS_SENT_NUMBER, 0);
                mainActivity.updateServerRecordsSent(DeviceServiceConnection.this, topic, numberOfRecordsSent);
            }
        }
    };

    private boolean intentMatches(Intent intent, String action) {
        return intent.getAction().equals(action)
                && getServiceClassName().equals(intent.getStringExtra(DEVICE_SERVICE_CLASS));
    }

    public DeviceServiceConnection(@NonNull MainActivity mainActivity, @NonNull Parcelable.Creator<S> deviceStateCreator, String serviceClassName) {
        super(deviceStateCreator);
        this.mainActivity = mainActivity;
        this.serviceClassName = serviceClassName;
    }

    @Override
    public void onServiceConnected(final ComponentName className,
                                   IBinder service) {
        mainActivity.registerReceiver(statusReceiver,
                new IntentFilter(DEVICE_STATUS_CHANGED));
        mainActivity.registerReceiver(serverStatusListener,
                new IntentFilter(SERVER_STATUS_CHANGED));
        mainActivity.registerReceiver(serverStatusListener,
                new IntentFilter(SERVER_RECORDS_SENT_TOPIC));

        if (!hasService()) {
            super.onServiceConnected(className, service);
            mainActivity.serviceConnected(this);

            if (isRemoteService()) {
                try {
                    getServiceBinder().linkToDeath(new IBinder.DeathRecipient() {
                        @Override
                        public void binderDied() {
                            mainActivity.deviceStatusUpdated(DeviceServiceConnection.this, getDeviceStatus());
                            onServiceDisconnected(className);
                        }
                    }, 0);
                } catch (RemoteException e) {
                    logger.error("Failed to link to death", e);
                }
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        boolean hadService = hasService();
        super.onServiceDisconnected(className);

        if (hadService) {
            mainActivity.unregisterReceiver(statusReceiver);
            mainActivity.unregisterReceiver(serverStatusListener);
            mainActivity.serviceDisconnected(this);
        }
    }

    public void bind(@NonNull Intent intent) {
        logger.info("Intending to start E4 service");

        mainActivity.startService(intent);
        mainActivity.bindService(intent, this, Context.BIND_ABOVE_CLIENT);
    }

    public void unbind() {
        mainActivity.unbindService(this);
        onServiceDisconnected(null);
    }

    public String getServiceClassName() {
        return serviceClassName;
    }
}
