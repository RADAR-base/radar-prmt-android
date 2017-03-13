package org.radarcns.android;

import android.os.RemoteException;

public interface MainActivityView {
    /**
     * Update the user interface.
     * @throws RemoteException if data could not be retrieved from one or more device services.
     */
    void update() throws RemoteException;
}
