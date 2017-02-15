package org.radarcns.application;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.BaseDeviceState;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.util.DeviceStateCreator;

import java.util.concurrent.atomic.AtomicInteger;

public class ApplicationState extends BaseDeviceState {
    private ServerStatusListener.Status serverStatus;
    private final AtomicInteger recordsSent = new AtomicInteger(0);

    public static final Parcelable.Creator<ApplicationState> CREATOR = new DeviceStateCreator<>(ApplicationState.class);

    public void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
        serverStatus = ServerStatusListener.Status.values()[in.readInt()];
        recordsSent.set(in.readInt());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(getServerStatus().ordinal());
        dest.writeInt(getRecordsSent());
    }

    public synchronized void setServerStatus(ServerStatusListener.Status status) {
        serverStatus = status;
    }

    public void addRecordsSent(int nRecords) {
        recordsSent.addAndGet(nRecords);
    }

    public synchronized ServerStatusListener.Status getServerStatus() {
        if (serverStatus == null) {
            return ServerStatusListener.Status.DISCONNECTED;
        }
        return serverStatus;
    }

    public int getRecordsSent() {
        return recordsSent.get();
    }
}
