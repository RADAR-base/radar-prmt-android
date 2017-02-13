package org.radarcns.application;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.DeviceState;
import org.radarcns.kafka.rest.ServerStatusListener;


public class ApplicationState extends DeviceState {

    private ServerStatusListener.Status serverStatus;
    private int combinedTotalRecordsSent;

    public static final Parcelable.Creator<ApplicationState> CREATOR = new Parcelable.Creator<ApplicationState>() {
        public ApplicationState createFromParcel(Parcel in) {
            ApplicationState result = new ApplicationState();
            result.updateFromParcel(in);
            return result;
        }

        public ApplicationState[] newArray(int size) {
            return new ApplicationState[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    protected void updateFromParcel(Parcel in) {
        super.updateFromParcel(in);
    }

    @Override
    public void updateServerStatus(ServerStatusListener.Status status) {
        serverStatus = status;
    }

    @Override
    public void updateCombinedTotalRecordsSent(int nRecords) {
        combinedTotalRecordsSent = nRecords;
    }

    public ServerStatusListener.Status getServerStatus() {
        if (serverStatus == null) {
            return ServerStatusListener.Status.DISCONNECTED;
        }
        return serverStatus;
    }

    public int getCombinedTotalRecordsSent() {
        return combinedTotalRecordsSent;
    }
}
