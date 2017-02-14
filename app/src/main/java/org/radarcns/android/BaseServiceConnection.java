package org.radarcns.android;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Pair;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.AvroDecoder;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordDecoder;
import org.radarcns.empaticaE4.E4DeviceStatus;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.radarcns.android.DeviceService.TRANSACT_GET_CACHE_SIZE;
import static org.radarcns.android.DeviceService.TRANSACT_GET_DEVICE_NAME;
import static org.radarcns.android.DeviceService.TRANSACT_UPDATE_CONFIG;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_DEVICE_STATUS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_RECORDS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_GET_SERVER_STATUS;
import static org.radarcns.empaticaE4.E4Service.TRANSACT_START_RECORDING;

public class BaseServiceConnection<S extends BaseDeviceState> implements ServiceConnection {
    private static final Logger logger = LoggerFactory.getLogger(BaseServiceConnection.class);
    private final Parcelable.Creator<S> deviceStateCreator;
    private boolean isRemote;
    private DeviceStatusListener.Status deviceStatus;
    public String deviceName;
    private IBinder serviceBinder;
    private final String serviceClassName;

    public BaseServiceConnection(Parcelable.Creator<S> deviceStateCreator, String serviceClassName) {
        this.serviceBinder = null;
        this.deviceName = null;
        this.deviceStatus = DeviceStatusListener.Status.DISCONNECTED;
        this.isRemote = false;
        this.deviceStateCreator = deviceStateCreator;
        this.serviceClassName = serviceClassName;
    }

    @Override
    public void onServiceConnected(final ComponentName className,
                                   IBinder service) {
        if (serviceBinder == null) {
            logger.info("Bound to service {}", className);
            serviceBinder = service;

            // We've bound to the running Service, cast the IBinder and get instance
            if (!(serviceBinder instanceof DeviceServiceBinder)) {
                isRemote = true;
            }
            try {
                deviceStatus = getDeviceData().getStatus();
            } catch (RemoteException e) {
                logger.error("Failed to get device status", e);
            }
        } else {
            logger.info("Trying to re-bind service, from {} to {}", serviceBinder, service);
        }
    }

    public <V extends SpecificRecord> List<Record<MeasurementKey, V>> getRecords(@NonNull AvroTopic<MeasurementKey, V> topic, int limit) throws RemoteException, IOException {
        LinkedList<Record<MeasurementKey, V>> result = new LinkedList<>();

        if (isRemote) {
            Parcel data = Parcel.obtain();
            data.writeString(topic.getName());
            data.writeInt(limit);
            Parcel reply = Parcel.obtain();
            serviceBinder.transact(TRANSACT_GET_RECORDS, data, reply, 0);
            AvroDecoder.AvroReader<MeasurementKey> keyDecoder = new SpecificRecordDecoder(true).reader(topic.getKeySchema(), MeasurementKey.class);
            AvroDecoder.AvroReader<V> valueDecoder = new SpecificRecordDecoder(true).reader(topic.getValueSchema(), topic.getValueClass());

            int len = reply.readInt();
            for (int i = 0; i < len; i++) {
                long offset = reply.readLong();
                MeasurementKey key = keyDecoder.decode(reply.createByteArray());
                V value = valueDecoder.decode(reply.createByteArray());
                result.addFirst(new Record<>(offset, key, value));
            }
        } else {
            for (Record<MeasurementKey, V> record : ((DeviceServiceBinder)serviceBinder).getRecords(topic, limit)) {
                result.addFirst(record);
            }
        }

        return result;
    }

    public void startRecording(@NonNull Set<String> acceptableIds) throws RemoteException {
        if (isRemote) {
            Parcel data = Parcel.obtain();
            data.writeInt(acceptableIds.size());
            for (String acceptableId : acceptableIds) {
                data.writeString(acceptableId);
            }
            Parcel reply = Parcel.obtain();
            serviceBinder.transact(TRANSACT_START_RECORDING, data, reply, 0);
            deviceStatus = E4DeviceStatus.CREATOR.createFromParcel(reply).getStatus();
        } else {
            deviceStatus = ((DeviceServiceBinder)serviceBinder).startRecording(acceptableIds).getStatus();
        }
    }

    public void stopRecording() throws RemoteException {
        if (isRemote) {
            Parcel data = Parcel.obtain();
            serviceBinder.transact(TRANSACT_START_RECORDING, data, null, 0);
        } else {
            ((DeviceServiceBinder)serviceBinder).stopRecording();
        }
    }

    public boolean isRecording() {
        return deviceStatus != DeviceStatusListener.Status.DISCONNECTED;
    }

    public boolean hasService() {
        return serviceBinder != null;
    }

    public ServerStatusListener.Status getServerStatus() throws RemoteException {
        if (isRemote) {
            Parcel reply = Parcel.obtain();
            serviceBinder.transact(TRANSACT_GET_SERVER_STATUS, Parcel.obtain(), reply, 0);
            return ServerStatusListener.Status.values()[reply.readInt()];
        } else {
            return ((DeviceServiceBinder)serviceBinder).getServerStatus();
        }
    }

    public Map<String, Integer> getServerSent() throws RemoteException {
        if (isRemote) {
            throw new RemoteException("No parcel for server records sent implemented");
        } else {
            return ((DeviceServiceBinder) serviceBinder).getServerRecordsSent();
        }
    }

    public S getDeviceData() throws RemoteException {
        if (isRemote) {
            if (deviceStateCreator == null) {
                throw new IllegalStateException(
                        "Cannot deserialize state without device state creator");
            }
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            serviceBinder.transact(TRANSACT_GET_DEVICE_STATUS, data, reply, 0);

            return deviceStateCreator.createFromParcel(reply);
        } else {
            //noinspection unchecked
            return (S)((DeviceServiceBinder)serviceBinder).getDeviceStatus();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        // Do NOT set deviceName to null. This causes loss of the name if application loses
        // focus [MM 2016-11-16]

        // only do these steps once
        if (serviceBinder != null) {
            serviceBinder = null;
            deviceStatus = DeviceStatusListener.Status.DISCONNECTED;
        }
    }

    public String getDeviceName() {
        if (isRemote) {
            try {
                Parcel reply = Parcel.obtain();
                serviceBinder.transact(TRANSACT_GET_DEVICE_NAME, Parcel.obtain(), reply, 0);
                deviceName = reply.readString();
            } catch (RemoteException ex) {
                // return initial device name
            }
        } else {
            deviceName = ((DeviceServiceBinder)serviceBinder).getDeviceName();
        }
        return deviceName;
    }

    public void updateConfiguration(Bundle bundle) {
        if (isRemote) {
            try {
                Parcel data = Parcel.obtain();
                data.writeBundle(bundle);
                serviceBinder.transact(TRANSACT_UPDATE_CONFIG, data, Parcel.obtain(), 0);
            } catch (RemoteException ex) {
                // keep old configuration
            }
        } else {
            if (serviceBinder != null) {
                ((DeviceServiceBinder) serviceBinder).updateConfiguration(bundle);
            }
        }
    }

    public Pair<Long, Long> numberOfRecords() throws RemoteException {
        if (isRemote) {
            Parcel reply = Parcel.obtain();
            serviceBinder.transact(TRANSACT_GET_CACHE_SIZE, Parcel.obtain(), reply, 0);
            return new Pair<>(reply.readLong(), reply.readLong());
        } else {
            return ((DeviceServiceBinder) serviceBinder).numberOfRecords();
        }
    }

    /**
     * True if given string is a substring of the device name.
     */
    public boolean isAllowedDevice(String[] values) {
        for(String value : values) {
            Pattern pattern = Strings.containsIgnoreCasePattern(value);
            String deviceName = getDeviceName();
            if (deviceName != null && pattern.matcher(deviceName).find()) {
                return true;
            }

            String sourceId;
            try {
                sourceId = getDeviceData().getId().getSourceId();
            } catch (RemoteException ex) {
                return false;
            }

            if (sourceId != null && pattern.matcher(sourceId).find()) {
                return true;
            }
        }
        return false;
    }

    public DeviceStatusListener.Status getDeviceStatus() {
        return deviceStatus;
    }

    protected void setDeviceStatus(DeviceStatusListener.Status status) {
        this.deviceStatus = status;
    }

    protected boolean isRemoteService() {
        return isRemote;
    }

    protected IBinder getServiceBinder() {
        return serviceBinder;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }
}
