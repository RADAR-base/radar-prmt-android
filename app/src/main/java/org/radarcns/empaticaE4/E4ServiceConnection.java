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
import org.radarcns.data.AvroDecoder;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordDecoder;
import org.radarcns.kafka.AvroTopic;
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

/**
 * Created by joris on 26/10/2016.
 */
class E4ServiceConnection implements ServiceConnection {
    private final static Logger logger = LoggerFactory.getLogger(E4ServiceConnection.class);
    private final MainActivity mainActivity;
    private E4DeviceStatusListener.Status deviceStatus;
    public String deviceName;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DEVICE_STATUS_CHANGED) && intent.getStringExtra(DEVICE_STATUS_SERVICE_CLASS).endsWith(String.valueOf(clsNumber))) {
                if (intent.hasExtra(DEVICE_STATUS_NAME)) {
                    deviceName = intent.getStringExtra(DEVICE_STATUS_NAME);
                }
                deviceStatus = E4DeviceStatusListener.Status.values()[intent.getIntExtra(DEVICE_STATUS_CHANGED, 0)];
                mainActivity.deviceStatusUpdated(E4ServiceConnection.this, deviceStatus);
                if (deviceStatus == E4DeviceStatusListener.Status.DISCONNECTED) {
                    serviceBinder = null;
                }
            }
        }
    };
    final int clsNumber;
    private IBinder serviceBinder;

    E4ServiceConnection(MainActivity mainActivity, int clsNumber) {
        this.mainActivity = mainActivity;
        this.clsNumber = clsNumber;
        this.serviceBinder = null;
        this.deviceName = null;
        this.deviceStatus = E4DeviceStatusListener.Status.DISCONNECTED;
        IntentFilter filter = new IntentFilter(DEVICE_STATUS_CHANGED);
        mainActivity.registerReceiver(statusReceiver, filter);
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        serviceBinder = service;

        // We've bound to the running Service, cast the IBinder and get instance
        mainActivity.serviceConnected(this);
        try {
            this.serviceBinder.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    mainActivity.deviceStatusUpdated(E4ServiceConnection.this, E4DeviceStatusListener.Status.DISCONNECTED);
                    serviceBinder = null;
                }
            }, 0);
        } catch (RemoteException e) {
            logger.error("Failed to link to death", e);
        }
    }

    public <V extends SpecificRecord> List<Record<MeasurementKey, V>> getRecords(AvroTopic topic, int limit) throws RemoteException, IOException {
        Parcel data = Parcel.obtain();
        data.writeString(topic.getName());
        data.writeInt(limit);
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_GET_RECORDS, data, reply, 0);
        AvroDecoder.AvroReader<MeasurementKey> keyDecoder = new SpecificRecordDecoder<MeasurementKey>(true).reader(topic.getKeySchema());
        AvroDecoder.AvroReader<V> valueDecoder = new SpecificRecordDecoder<V>(true).reader(topic.getValueSchema());

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

    public E4DeviceStatus getDeviceData() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        serviceBinder.transact(TRANSACT_GET_DEVICE_STATUS, data, reply, 0);

        return E4DeviceStatus.CREATOR.createFromParcel(reply);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        serviceBinder = null;
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

    public boolean hasService() {
        return serviceBinder != null;
    }

    public void close() {
        mainActivity.unregisterReceiver(statusReceiver);
    }

    public String getDeviceName() {
        return deviceName;
    }
}
