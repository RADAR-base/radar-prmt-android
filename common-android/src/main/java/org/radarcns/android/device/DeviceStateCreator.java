package org.radarcns.android.device;

import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Array;

/**
 * Creator from a parcel to a given BaseDeviceState subclass.
 */
public class DeviceStateCreator<T extends BaseDeviceState> implements Parcelable.Creator<T> {
    private final Class<T> stateClass;

    public DeviceStateCreator(Class<T> stateClass) {
        this.stateClass = stateClass;
    }

    @Override
    public T createFromParcel(Parcel source) {
        try {
            T state = stateClass.newInstance();
            state.updateFromParcel(source);
            return state;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new IllegalStateException("Cannot instantiate state class", ex);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T[] newArray(int size) {
        return (T[])Array.newInstance(stateClass, size);
    }
}
