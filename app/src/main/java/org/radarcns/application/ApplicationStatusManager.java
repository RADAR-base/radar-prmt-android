package org.radarcns.application;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Pair;

import org.radarcns.R;
import org.radarcns.android.BaseServiceConnection;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceServiceBinder;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.empaticaE4.E4Service;
import org.radarcns.pebble2.Pebble2Service;
import org.radarcns.phone.PhoneSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.content.Context.BIND_WAIVE_PRIORITY;

public class ApplicationStatusManager implements DeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStatusManager.class);
    private static final long APPLICATION_UPDATE_INTERVAL_DEFAULT = 5*60; // seconds
    private static final Class[] APPLICATION_SERVICES_CLASSES = {
            E4Service.class, PhoneSensorsService.class, Pebble2Service.class};

    private final TableDataHandler dataHandler;
    private final Context context;

    private final ApplicationStatusService applicationStatusService;

    private final MeasurementTable<ApplicationServerStatus> serverStatusTable;
    private final MeasurementTable<ApplicationUptime> uptimeTable;
    private final MeasurementTable<ApplicationRecordCounts> recordCountsTable;

    private final ApplicationState deviceStatus;

    private String deviceName;
    private ScheduledFuture<?> serverStatusUpdateFuture;
    private final ScheduledExecutorService executor;

    private final List<BaseServiceConnection<?>> services;

    private final long creationTimeStamp;
    private boolean isRegistered = false;

    public ApplicationStatusManager(Context context, ApplicationStatusService applicationStatusService, String groupId, String sourceId, TableDataHandler dataHandler, ApplicationStatusTopics topics) {
        this.dataHandler = dataHandler;
        this.serverStatusTable = dataHandler.getCache(topics.getServerTopic());
        this.uptimeTable = dataHandler.getCache(topics.getUptimeTopic());
        this.recordCountsTable = dataHandler.getCache(topics.getRecordCountsTopic());

        this.applicationStatusService = applicationStatusService;

        this.context = context;
//        sensorManager = null;
        this.deviceStatus = new ApplicationState();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceStatus.getId().setSourceId(sourceId);
        deviceName = context.getString(R.string.app_name);
//        updateStatus(DeviceStatusListener.Status.READY);

        services = new ArrayList<>(APPLICATION_SERVICES_CLASSES.length);
        creationTimeStamp = System.currentTimeMillis();

        // Scheduler TODO: run executor with existing thread pool/factory
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void start(@NonNull Set<String> acceptableIds) {
        for (Class clazz : APPLICATION_SERVICES_CLASSES) {
            Intent serviceIntent = new Intent(context, clazz);
            @SuppressWarnings("unchecked")
            BaseServiceConnection conn = new BaseServiceConnection(null);
            services.add(conn);
            context.bindService(serviceIntent, conn, BIND_WAIVE_PRIORITY);
        }

        // Application status
        setApplicationStatusUpdateRate(APPLICATION_UPDATE_INTERVAL_DEFAULT);

        isRegistered = true;
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setApplicationStatusUpdateRate(final long period) {
        if (serverStatusUpdateFuture != null) {
            serverStatusUpdateFuture.cancel(false);
        }

        serverStatusUpdateFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    processServerStatus();
                    processUptime();
                    processRecordsSent();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("App status updater: listener activated and set to a period of {}", period);
    }

    @Override
    public boolean isClosed() {
        return !isRegistered;
    }

    @Override
    public DeviceState getState() {
        return deviceStatus;
    }

    @Override
    public String getName() {
        return deviceName;
    }

    public void processServerStatus() {
        double timeReceived = System.currentTimeMillis() / 1_000d;

        deviceStatus.updateServerStatus(((DeviceServiceBinder)applicationStatusService.getBinder()).getServerStatus());

        for (BaseServiceConnection<?> conn : services) {
            if (conn.hasService()) {
                try {
                    deviceStatus.updateServerStatus(conn.getServerStatus());
                } catch (RemoteException e) {
                    logger.warn("Failed to get server status from connection");
                }
            }
        }

        ServerStatus status;
        switch (deviceStatus.getServerStatus()) {
            case CONNECTED:
            case READY:
            case UPLOADING:
                status = ServerStatus.CONNECTED;
                break;
            case DISCONNECTED:
            case DISABLED:
            case UPLOADING_FAILED:
                status = ServerStatus.DISCONNECTED;
                break;
            default:
                status = ServerStatus.UNKNOWN;
        }
        String ipAddress = getIpAddress();
        logger.info("Server Status: {}; Device IP: {}", status, ipAddress);

        ApplicationServerStatus value = new ApplicationServerStatus(timeReceived, timeReceived, status, ipAddress);

        dataHandler.addMeasurement(serverStatusTable, deviceStatus.getId(), value);
    }

    private String getIpAddress() {
        // Find Ip via NetworkInterfaces. Works via wifi, ethernet and mobile network
        String ipAddress = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        // This finds both xx.xx.xx ip and rmnet. Last one is always ip.
                        ipAddress = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            return null;
        }
        return ipAddress;
    }

    public void processUptime() {
        double timeReceived = System.currentTimeMillis() / 1_000d;

        double uptime = (System.currentTimeMillis() - creationTimeStamp)/1000d;
        ApplicationUptime value = new ApplicationUptime(timeReceived, timeReceived, uptime);

        dataHandler.addMeasurement(uptimeTable, deviceStatus.getId(), value);
    }

    public void processRecordsSent() {
        double timeReceived = System.currentTimeMillis() / 1_000d;

        Pair<Long, Long> localRecords = ((DeviceServiceBinder)applicationStatusService.getBinder()).numberOfRecords();
        int recordsCachedUnsent = localRecords.first.intValue();
        int recordsCachedSent = localRecords.second.intValue();

        for (BaseServiceConnection<?> conn : services) {
            if (conn.hasService()) {
                try {
                    Pair<Long, Long> numRecords = conn.numberOfRecords();
                    recordsCachedUnsent += numRecords.first.intValue();
                    recordsCachedSent += numRecords.second.intValue();
                } catch (RemoteException e) {
                    logger.warn("Failed to get server status from connection");
                }
            }
        }

        int recordsCached = recordsCachedUnsent + recordsCachedSent;
        int recordsSent = deviceStatus.getCombinedTotalRecordsSent();

        logger.info("Number of records: {sent: {}, unsent: {}, cached: {}}",
                recordsSent, recordsCachedUnsent, recordsCached);
        ApplicationRecordCounts value = new ApplicationRecordCounts(timeReceived, timeReceived,
                recordsCached, recordsSent, recordsCachedUnsent);
        dataHandler.addMeasurement(recordCountsTable, deviceStatus.getId(), value);
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
        for (BaseServiceConnection<?> conn : services) {
            context.unbindService(conn);
        }
        isRegistered = false;
        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.applicationStatusService.deviceStatusUpdated(this, status);
    }
}
