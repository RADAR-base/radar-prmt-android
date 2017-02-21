package org.radarcns.biovotionVSM;

import android.content.Context;
import android.support.annotation.NonNull;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.TableDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Manages scanning for a Biovotion VSM wearable and connecting to it */
public class BiovotionDeviceManager implements DeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(BiovotionDeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;
    private final DeviceStatusListener biovotionService;

    private final BiovotionDeviceStatus deviceStatus;

    private boolean isClosed;
    private final String deviceName;

    public BiovotionDeviceManager(Context context, DeviceStatusListener biovotionService, String groupId, TableDataHandler handler, BiovotionTopics topics) {
        this.dataHandler = handler;

        this.biovotionService = biovotionService;
        this.context = context;

        this.deviceStatus = new BiovotionDeviceStatus();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceName = null;
        this.isClosed = true;
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        this.isClosed = false;
    }

    @Override
    public void close() {
        this.isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public BiovotionDeviceStatus getState() {
        return deviceStatus;
    }

    @Override
    public String getName() {
        return deviceName;
    }
}
