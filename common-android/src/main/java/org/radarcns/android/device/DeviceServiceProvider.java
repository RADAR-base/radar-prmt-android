package org.radarcns.android.device;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.radarcns.android.MainActivity;
import org.radarcns.android.RadarConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * RADAR service provider, to bind and configure to a service. It is not thread-safe.
 * @param <T> state that the Service will provide.
 */
public abstract class DeviceServiceProvider<T extends BaseDeviceState> {
    private MainActivity activity;
    private RadarConfiguration config;
    private DeviceServiceConnection<T> connection;
    private boolean bound;

    /**
     * Class of the service.
     * @return non-null DeviceService
     */
    public abstract Class<?> getServiceClass();

    /**
     * Creator for a device state.
     * @return non-null state creator.
     */
    public abstract Parcelable.Creator<T> getStateCreator();

    /** Display name of the service. */
    public abstract String getDisplayName();

    /**
     * Whether the service has a UI detail view that can be invoked. If not,
     * {@link #showDetailView()} will throw an UnsupportedOperationException.
     */
    public boolean hasDetailView() {
        return false;
    }

    /**
     * Show a detail view from the MainActivity.
     * @throws UnsupportedOperationException if {@link #hasDetailView()} is false.
     */
    public void showDetailView() {
        throw new UnsupportedOperationException();
    }

    public DeviceServiceProvider() {
        bound = false;
    }

    /**
     * Get or create a DeviceServiceConnection. Once created, it will be a single fixed connection
     * object.
     * @throws IllegalStateException if {@link #setActivity(MainActivity)} has not been called.
     * @throws UnsupportedOperationException if {@link #getStateCreator()} or
     *                                       {@link #getServiceClass()} returns null.
     */
    public DeviceServiceConnection<T> getConnection() {
        if (connection == null) {
            if (activity == null) {
                throw new IllegalStateException("#setActivity(MainActivity) needs to be set before #getConnection() is called.");
            }
            Parcelable.Creator<T> creator = getStateCreator();
            if (creator == null) {
                throw new UnsupportedOperationException("RadarServiceProvider " + getClass().getSimpleName() + " does not provide state creator");
            }

            Class<?> serviceClass = getServiceClass();
            if (serviceClass == null) {
                throw new UnsupportedOperationException("RadarServiceProvider " + getClass().getSimpleName() + " does not provide service class");
            }
            connection = new DeviceServiceConnection<>(activity, creator, serviceClass.getName());
        }
        return connection;
    }

    /**
     * Bind the service to the MainActivity. Call this when the {@link MainActivity#onStart()} is
     * called.
     * @throws IllegalStateException if {@link #setActivity(MainActivity)} and
     *                               {@link #setConfig(RadarConfiguration)} have not been called or
     *                               if the service is already bound.
     */
    public void bind() {
        if (activity == null) {
            throw new IllegalStateException(
                    "#setActivity(MainActivity) needs to be set before #bind() is called.");
        }
        if (config == null) {
            throw new IllegalStateException(
                    "#setConfig(RadarConfiguration) needs to be set before #bind() is called.");
        }
        if (bound) {
            throw new IllegalStateException("Service is already bound");
        }
        Intent intent = new Intent(activity, getServiceClass());
        Bundle extras = new Bundle();
        configure(extras);
        intent.putExtras(extras);

        activity.startService(intent);
        activity.bindService(intent, getConnection(), Context.BIND_ABOVE_CLIENT);

        bound = true;
    }

    /**
     * Unbind the service from the MainActivity. Call this when the {@link MainActivity#onStop()} is
     * called.
     */
    public void unbind() {
        if (!bound) {
            throw new IllegalStateException("Service is not bound");
        }
        if (activity == null) {
            throw new IllegalStateException("#setActivity(MainActivity) needs to be set before #bind() is called.");
        }
        activity.unbindService(connection);
        connection.onServiceDisconnected(null);
    }

    /**
     * Update the configuration of the service based on the given RadarConfiguration.
     * @throws IllegalStateException if {@link #getConnection()} has not been called
     *                               yet.
     */
    public void updateConfiguration() {
        if (config == null) {
            throw new IllegalStateException("#setConfig(RadarConfiguration) needs to be set before #bind() is called.");
        }
        if (connection == null) {
            throw new IllegalStateException("#getConnection() has not yet been called.");
        }
        if (connection.hasService()) {
            Bundle bundle = new Bundle();
            configure(bundle);
            connection.updateConfiguration(bundle);
        }
    }

    /**
     * Configure the service from the set RadarConfiguration.
     * Override and call {@code super.configure(bundle)} to pass additional options.
     */
    protected void configure(Bundle bundle) {
        // Add the default configuration parameters given to the service intents
        config.putExtras(bundle,
                RadarConfiguration.KAFKA_REST_PROXY_URL_KEY, RadarConfiguration.SCHEMA_REGISTRY_URL_KEY, RadarConfiguration.DEFAULT_GROUP_ID_KEY,
                RadarConfiguration.KAFKA_UPLOAD_RATE_KEY, RadarConfiguration.KAFKA_CLEAN_RATE_KEY, RadarConfiguration.KAFKA_RECORDS_SEND_LIMIT_KEY,
                RadarConfiguration.SENDER_CONNECTION_TIMEOUT_KEY);
    }

    /**
     * Loads the service providers specified in the
     * {@link RadarConfiguration#DEVICE_SERVICES_TO_CONNECT}. This function will call
     * {@link #setActivity(MainActivity)} and {@link #setConfig(RadarConfiguration)} on each of the
     * loaded service providers.
     */
    public static List<DeviceServiceProvider> loadProviders(@NonNull MainActivity activity,
                                                            @NonNull RadarConfiguration config) {
        List<DeviceServiceProvider> factories = new ArrayList<>();
        String value = config.getString(RadarConfiguration.DEVICE_SERVICES_TO_CONNECT);
        Scanner scanner = new Scanner(value);
        while (scanner.hasNext()) {
            String className = scanner.next();
            if (className.charAt(0) == '.') {
                className = "org.radarcns" + className;
            }
            Class<?> factoryClass;
            try {
                factoryClass = Class.forName(className);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Class " + className + " not found in classpath", ex);
            }
            try {
                DeviceServiceProvider serviceProvider = (DeviceServiceProvider)factoryClass.newInstance();
                serviceProvider.setActivity(activity);
                serviceProvider.setConfig(config);
                factories.add(serviceProvider);
            } catch (InstantiationException | IllegalAccessException ex) {
                throw new IllegalArgumentException("Class " + className + " cannot be instantiated", ex);
            } catch (ClassCastException ex) {
                throw new IllegalArgumentException("Class " + className + " is not a " + DeviceServiceProvider.class.getSimpleName());
            }
        }
        return factories;
    }

    /** Get the MainActivity associated to the current connection. */
    public MainActivity getActivity() {
        return this.activity;
    }

    /**
     * Associate a MainActivity with a new connection.
     * @throws NullPointerException if given activity is null
     * @throws IllegalStateException if the connection has already been started.
     */
    public void setActivity(@NonNull MainActivity activity) {
        if (this.connection != null) {
            throw new IllegalStateException(
                    "Cannot change the MainActivity after a connection has been started.");
        }
        Objects.requireNonNull(activity);
        this.activity = activity;
    }

    /** Get the RadarConfiguration currently set for the service provider. */
    public RadarConfiguration getConfig() {
        return config;
    }

    /** Whether {@link #getConnection()} has already been called. */
    public boolean hasConnection() {
        return connection != null;
    }

    /** Set the config. To update a running service with given config, call
     * {@link #updateConfiguration()}.
     * @throws NullPointerException if the configuration is null.
     */
    public void setConfig(@NonNull RadarConfiguration config) {
        Objects.requireNonNull(config);
        this.config = config;
    }

    /**
     * Whether {@link #bind()} has been called and {@link #unbind()} has not been called since then.
     * @return true if bound, false otherwise
     */
    public boolean isBound() {
        return bound;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + getServiceClass().getSimpleName()  + ">";
    }

    /** Whether the current service can meaningfully be displayed. */
    public boolean isDisplayable() {
        return true;
    }

    /**
     * Whether the device name should be checked with given filters before a connection is allowed
     */
    public boolean isFilterable() { return false; }

    /**
     * Android permissions that the underlying service needs to function correctly.
     */
    public abstract List<String> needsPermissions();
}
