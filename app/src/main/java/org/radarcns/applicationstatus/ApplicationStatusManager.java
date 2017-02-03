package org.radarcns.applicationstatus;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.text.format.Formatter;

import org.radarcns.R;
import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceState;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.kafka.rest.ServerStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.content.Context.WIFI_SERVICE;

public class ApplicationStatusManager implements ServerStatusListener, DeviceManager {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStatusManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;

    private final DeviceStatusListener applicationStatusService;

    private final MeasurementTable<ApplicationStatusServer> applicationStatus;

    private final ApplicationStatusState deviceStatus;

    private String deviceName;
    private boolean isRegistered = false;
    private ScheduledFuture<?> serverStatusUpdateFuture;
    private final ScheduledExecutorService executor;

    private final long APPLICATION_UPDATE_INTERVAL_DEFAULT = 5;//*60; // seconds

    public ApplicationStatusManager(Context context, DeviceStatusListener applicationStatusService, String groupId, String sourceId, TableDataHandler dataHandler, ApplicationStatusTopics topics) {
        this.dataHandler = dataHandler;
        this.applicationStatus = dataHandler.getCache(topics.getServerTopic());

        this.applicationStatusService = applicationStatusService;

        this.context = context;
//        sensorManager = null;
        this.deviceStatus = new ApplicationStatusState();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceStatus.getId().setSourceId(sourceId);
        deviceName = context.getString(R.string.app_name);
//        updateStatus(DeviceStatusListener.Status.READY);

        // Scheduler TODO: run executor with existing thread pool/factory
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void updateServerStatus(Status status) {

    }

    @Override
    public void updateRecordsSent(String topicName, int numberOfRecords) {

    }

    @Override
    public void start(@NonNull Set<String> acceptableIds) {
        // Application status
        setApplicationStatusUpdateRate(APPLICATION_UPDATE_INTERVAL_DEFAULT);

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setApplicationStatusUpdateRate(final long period) {
        if (serverStatusUpdateFuture != null) {
            serverStatusUpdateFuture.cancel(false);
        }

        serverStatusUpdateFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();

                String ipAddress = Formatter.formatIpAddress(ip);
                logger.info("Server Status: {}", deviceStatus.getServerStatus());
                try {
                    logger.info("IP: {}", InetAddress.getLocalHost());
                    logger.info("IP2: {}", ipAddress);
                } catch (UnknownHostException ex) {

                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("App status updater: listener activated and set to a period of {}", period);
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public DeviceState getState() {
        return deviceStatus;
    }

    @Override
    public String getName() {
        return deviceName;
    }

    @Override
    public void close() throws IOException {

    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
    }
}
