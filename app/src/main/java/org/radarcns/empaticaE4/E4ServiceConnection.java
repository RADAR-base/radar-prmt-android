package org.radarcns.empaticaE4;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.data.AvroDecoder;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordDecoder;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_CHANGED;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_NAME;
import static org.radarcns.empaticaE4.E4Service.DEVICE_STATUS_SERVICE_CLASS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_DEVICE_STATUS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_RECORDS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_SERVER_STATUS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_START_RECORDING;

class E4ServiceConnection implements ServiceConnection {
    private final static Logger logger = LoggerFactory.getLogger(E4ServiceConnection.class);
    private final MainActivity mainActivity;
    private DeviceStatusListener.Status deviceStatus;
    private final int clsNumber;
    public String deviceName;
    private IBinder serviceBinder;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DEVICE_STATUS_CHANGED) && intent.getStringExtra(DEVICE_STATUS_SERVICE_CLASS).endsWith(String.valueOf(clsNumber))) {
                if (intent.hasExtra(DEVICE_STATUS_NAME)) {
                    deviceName = intent.getStringExtra(DEVICE_STATUS_NAME);
                }
                deviceStatus = DeviceStatusListener.Status.values()[intent.getIntExtra(DEVICE_STATUS_CHANGED, 0)];
                mainActivity.deviceStatusUpdated(E4ServiceConnection.this, deviceStatus);
            }
        }
    };

    E4ServiceConnection(MainActivity mainActivity, int clsNumber) {
        this.mainActivity = mainActivity;
        this.clsNumber = clsNumber;
        this.serviceBinder = null;
        this.deviceName = null;
        this.deviceStatus = DeviceStatusListener.Status.DISCONNECTED;
        IntentFilter filter = new IntentFilter(DEVICE_STATUS_CHANGED);
        mainActivity.registerReceiver(statusReceiver, filter);
    }

    @Override
    public void onServiceConnected(final ComponentName className,
                                   IBinder service) {
        logger.info("Bound to service {}", className);
        serviceBinder = service;

        // We've bound to the running Service, cast the IBinder and get instance
        mainActivity.serviceConnected(this);
        try {
            this.serviceBinder.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    onServiceDisconnected(className);
                    mainActivity.deviceStatusUpdated(E4ServiceConnection.this, deviceStatus);
                    mainActivity.bindToEmpatica(E4ServiceConnection.this);
                }
            }, 0);
        } catch (RemoteException e) {
            logger.error("Failed to link to death", e);
        }
        try {
            deviceStatus = getDeviceData().getStatus();
        } catch (RemoteException e) {
            logger.error("Failed to get device status", e);
        }
    }

    public <V extends SpecificRecord> List<Record<MeasurementKey, V>> getRecords(AvroTopic<MeasurementKey, V> topic, int limit) throws RemoteException, IOException {
        Parcel data = Parcel.obtain();
        data.writeString(topic.getName());
        data.writeInt(limit);
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_GET_RECORDS, data, reply, 0);
        AvroDecoder.AvroReader<MeasurementKey> keyDecoder = new SpecificRecordDecoder(true).reader(topic.getKeySchema(), MeasurementKey.class);
        AvroDecoder.AvroReader<V> valueDecoder = new SpecificRecordDecoder(true).reader(topic.getValueSchema(), topic.getValueClass());

        int len = reply.readInt();
        LinkedList<Record<MeasurementKey, V>> result = new LinkedList<>();
        for (int i = 0; i < len; i++) {
            long offset = reply.readLong();
            MeasurementKey key = keyDecoder.decode(reply.createByteArray());
            V value = valueDecoder.decode(reply.createByteArray());
            result.addFirst(new Record<>(offset, key, value));
        }

        return result;
    }

    public void startRecording() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_START_RECORDING, data, reply, 0);
        deviceStatus = E4DeviceStatus.CREATOR.createFromParcel(reply).getStatus();
    }

    public void stopRecording() throws RemoteException {
        Parcel data = Parcel.obtain();
        serviceBinder.transact(TRANSACT_START_RECORDING, data, null, 0);
    }

    public boolean isRecording() {
        return deviceStatus != DeviceStatusListener.Status.DISCONNECTED;
    }

    public boolean isScanning() {
        return deviceStatus == DeviceStatusListener.Status.READY;
    }

    public boolean hasService() {
        return serviceBinder != null;
    }

    public ServerStatusListener.Status getServerStatus() throws RemoteException {
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_GET_SERVER_STATUS, Parcel.obtain(), reply, 0);
        return ServerStatusListener.Status.values()[reply.readInt()];
    }

    public E4DeviceStatus getDeviceData() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_GET_DEVICE_STATUS, data, reply, 0);

        return E4DeviceStatus.CREATOR.createFromParcel(reply);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        serviceBinder = null;
        deviceName = null;
        deviceStatus = DeviceStatusListener.Status.DISCONNECTED;
    }

    Class<? extends E4Service> serviceClass() {
        switch (clsNumber) {
            case 0:
                return E4Service0.class;
            case 1:
                return E4Service1.class;
            case 2:
                return E4Service2.class;
            case 3:
                return E4Service3.class;
            default:
                return null;
        }
    }

    public void close() {
        mainActivity.unregisterReceiver(statusReceiver);
    }

    public String getDeviceName() {
        return deviceName;
    }
}
