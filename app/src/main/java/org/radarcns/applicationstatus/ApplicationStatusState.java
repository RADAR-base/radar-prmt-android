package org.radarcns.applicationstatus;

import android.os.Parcel;
import android.os.Parcelable;

import org.radarcns.android.DeviceState;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.radarcns.util.Serialization;


public class ApplicationStatusState extends DeviceState {

    private ServerStatusListener.Status serverStatus;
    private int combinedTotalRecordsSent;

    public static final Parcelable.Creator<ApplicationStatusState> CREATOR = new Parcelable.Creator<ApplicationStatusState>() {
        public ApplicationStatusState createFromParcel(Parcel in) {
            ApplicationStatusState result = new ApplicationStatusState();
            result.updateFromParcel(in);
            return result;
        }

        public ApplicationStatusState[] newArray(int size) {
            return new ApplicationStatusState[size];
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
