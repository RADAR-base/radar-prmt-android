package org.radarcns.android.device;

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
import org.radarcns.android.kafka.ServerStatusListener;
import org.radarcns.data.AvroDecoder;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordDecoder;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
        this.deviceStateCreator = Objects.requireNonNull(deviceStateCreator);
        this.serviceClassName = serviceClassName;
    }

    @Override
    public void onServiceConnected(final ComponentName className,
                                   IBinder service) {
        if (serviceBinder == null) {
            logger.info("Bound to service {}", className);
            synchronized (this) {
                serviceBinder = service;
            }

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
            getServiceBinder().transact(DeviceService.TRANSACT_GET_RECORDS, data, reply, 0);
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
            for (Record<MeasurementKey, V> record : getLocalServiceBinder().getRecords(topic, limit)) {
                result.addFirst(record);
            }
        }

        return result;
    }

    /**
     * Start looking for devices to record.
     * @param acceptableIds case insensitive parts of device ID's that are allowed to connect.
     * @throws RemoteException if the Service cannot be reached
     * @throws IllegalStateException if the user ID was not set yet
     */
    public void startRecording(@NonNull Set<String> acceptableIds) throws RemoteException {
        if (isRemote) {
            Parcel data = Parcel.obtain();
            data.writeInt(acceptableIds.size());
            for (String acceptableId : acceptableIds) {
                data.writeString(acceptableId);
            }
            Parcel reply = Parcel.obtain();
            getServiceBinder().transact(DeviceService.TRANSACT_START_RECORDING, data, reply, 0);
            if (reply.readByte() == 0) {
                throw new IllegalStateException("Cannot start recording: user ID was not set yet");
            }
            deviceStatus = deviceStateCreator.createFromParcel(reply).getStatus();
        } else {
            deviceStatus = getLocalServiceBinder().startRecording(acceptableIds).getStatus();
        }
    }

    public void stopRecording() throws RemoteException {
        if (isRemote) {
            Parcel data = Parcel.obtain();
            getServiceBinder().transact(DeviceService.TRANSACT_START_RECORDING, data, null, 0);
        } else {
            getLocalServiceBinder().stopRecording();
        }
    }

    public boolean isRecording() {
        return deviceStatus != DeviceStatusListener.Status.DISCONNECTED;
    }

    public boolean hasService() {
        return getServiceBinder() != null;
    }

    public ServerStatusListener.Status getServerStatus() throws RemoteException {
        if (isRemote) {
            Parcel reply = Parcel.obtain();
            getServiceBinder().transact(DeviceService.TRANSACT_GET_SERVER_STATUS, Parcel.obtain(), reply, 0);
            return ServerStatusListener.Status.values()[reply.readInt()];
        } else {
            return getLocalServiceBinder().getServerStatus();
        }
    }

    public Map<String, Integer> getServerSent() throws RemoteException {
        if (isRemote) {
            throw new RemoteException("No parcel for server records sent implemented");
        } else {
            return getLocalServiceBinder().getServerRecordsSent();
        }
    }

    public S getDeviceData() throws RemoteException {
        if (isRemote) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            getServiceBinder().transact(DeviceService.TRANSACT_GET_DEVICE_STATUS, data, reply, 0);

            return deviceStateCreator.createFromParcel(reply);
        } else {
            //noinspection unchecked
            return (S)getLocalServiceBinder().getDeviceStatus();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        // Do NOT set deviceName to null. This causes loss of the name if application loses
        // focus [MM 2016-11-16]

        // only do these steps once
        if (getServiceBinder() != null) {
            synchronized (this) {
                serviceBinder = null;
                deviceStatus = DeviceStatusListener.Status.DISCONNECTED;
            }
        }
    }

    public String getDeviceName() {
        if (isRemote) {
            try {
                Parcel reply = Parcel.obtain();
                getServiceBinder().transact(DeviceService.TRANSACT_GET_DEVICE_NAME, Parcel.obtain(), reply, 0);
                deviceName = reply.readString();
            } catch (RemoteException ex) {
                // return initial device name
            }
        } else {
            deviceName = getLocalServiceBinder().getDeviceName();
        }
        return deviceName;
    }

    public void updateConfiguration(Bundle bundle) {
        if (isRemote) {
            try {
                Parcel data = Parcel.obtain();
                data.writeBundle(bundle);
                getServiceBinder().transact(DeviceService.TRANSACT_UPDATE_CONFIG, data, Parcel.obtain(), 0);
            } catch (RemoteException ex) {
                // keep old configuration
            }
        } else {
            getLocalServiceBinder().updateConfiguration(bundle);
        }
    }

    public void setUserId(String userId) throws RemoteException {
        if (isRemoteService()) {
            Parcel data = Parcel.obtain();
            data.writeString(userId);
            getServiceBinder().transact(DeviceService.TRANSACT_SET_USER_ID, data, null, 0);
        } else {
            getLocalServiceBinder().setUserId(userId);
        }
    }

    public Pair<Long, Long> numberOfRecords() throws RemoteException {
        if (isRemote) {
            Parcel reply = Parcel.obtain();
            getServiceBinder().transact(DeviceService.TRANSACT_GET_CACHE_SIZE, Parcel.obtain(), reply, 0);
            return new Pair<>(reply.readLong(), reply.readLong());
        } else {
            return getLocalServiceBinder().numberOfRecords();
        }
    }

    /**
     * True if given string is a substring of the device name.
     */
    public boolean isAllowedDevice(Collection<String> values) {
        if (values.isEmpty()) {
            return true;
        }
        for (String value : values) {
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

    public synchronized DeviceStatusListener.Status getDeviceStatus() {
        return deviceStatus;
    }

    protected synchronized void setDeviceStatus(DeviceStatusListener.Status status) {
        this.deviceStatus = status;
    }

    protected boolean isRemoteService() {
        return isRemote;
    }

    protected synchronized IBinder getServiceBinder() {
        return serviceBinder;
    }

    protected synchronized DeviceServiceBinder getLocalServiceBinder() {
        if (isRemote) {
            throw new IllegalStateException("Cannot get local service binder for remote service");
        }
        return (DeviceServiceBinder)serviceBinder;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BaseServiceConnection otherService = (BaseServiceConnection) other;
        return Objects.equals(this.serviceClassName, otherService.serviceClassName);
    }

    @Override
    public int hashCode() {
        return serviceClassName.hashCode();
    }

    public String toString() {
        return getClass().getSimpleName() + "<" + getServiceClassName() + ">";
    }
}
