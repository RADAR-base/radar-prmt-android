package org.radarcns.biovotionVSM;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.NonNull;

import org.radarcns.android.DeviceManager;
import org.radarcns.android.DeviceStatusListener;
import org.radarcns.android.MeasurementTable;
import org.radarcns.android.TableDataHandler;
import org.radarcns.biovotion.BiovotionVSMBatteryState;
import org.radarcns.biovotion.BiovotionVSMBloodPulseWave;
import org.radarcns.biovotion.BiovotionVSMEnergy;
import org.radarcns.biovotion.BiovotionVSMGalvanicSkinResponse;
import org.radarcns.biovotion.BiovotionVSMHeartRate;
import org.radarcns.biovotion.BiovotionVSMHeartRateVariability;
import org.radarcns.biovotion.BiovotionVSMRespirationRate;
import org.radarcns.biovotion.BiovotionVSMSpO2;
import org.radarcns.biovotion.BiovotionVSMTemperature;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Pattern;

import ch.hevs.biovotion.vsm.ble.scanner.VsmDiscoveryListener;
import ch.hevs.biovotion.vsm.ble.scanner.VsmScanner;
import ch.hevs.biovotion.vsm.core.VsmConnectionState;
import ch.hevs.biovotion.vsm.core.VsmDescriptor;
import ch.hevs.biovotion.vsm.core.VsmDevice;
import ch.hevs.biovotion.vsm.core.VsmDeviceListener;
import ch.hevs.biovotion.vsm.protocol.stream.StreamValue;
import ch.hevs.biovotion.vsm.protocol.stream.units.Algo1;
import ch.hevs.biovotion.vsm.protocol.stream.units.Algo2;
import ch.hevs.biovotion.vsm.protocol.stream.units.BatteryState;
import ch.hevs.biovotion.vsm.protocol.stream.units.RawBoard;
import ch.hevs.biovotion.vsm.stream.StreamController;
import ch.hevs.biovotion.vsm.stream.StreamListener;
import ch.hevs.ble.lib.core.BleService;
import ch.hevs.ble.lib.exceptions.BleScanException;
import ch.hevs.ble.lib.scanner.Scanner;

/** Manages scanning for a Biovotion VSM wearable and connecting to it */
public class BiovotionDeviceManager implements DeviceManager, VsmDeviceListener, VsmDiscoveryListener, StreamListener {
    private static final Logger logger = LoggerFactory.getLogger(BiovotionDeviceManager.class);

    private final TableDataHandler dataHandler;
    private final Context context;
    private final DeviceStatusListener biovotionService;

    private final MeasurementTable<BiovotionVSMBloodPulseWave> bpwTable;
    private final MeasurementTable<BiovotionVSMSpO2> spo2Table;
    private final MeasurementTable<BiovotionVSMHeartRate> hrTable;
    private final MeasurementTable<BiovotionVSMHeartRateVariability> hrvTable;
    private final MeasurementTable<BiovotionVSMRespirationRate> rrTable;
    private final MeasurementTable<BiovotionVSMEnergy> energyTable;
    private final MeasurementTable<BiovotionVSMTemperature> temperatureTable;
    private final MeasurementTable<BiovotionVSMGalvanicSkinResponse> gsrTable;
    private final AvroTopic<MeasurementKey, BiovotionVSMBatteryState> batteryTopic;

    private final BiovotionDeviceStatus deviceStatus;

    private boolean isClosed;
    private String deviceName;
    private Pattern[] acceptableIds;

    private VsmDevice vsmDevice;
    private StreamController vsmStreamController;
    private VsmDescriptor vsmDescriptor;
    private VsmScanner vsmScanner;
    private BluetoothAdapter vsmBluetoothAdapter;
    private BleService vsmBleService;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            vsmBleService = ((BleService.LocalBinder) service).getService();
            vsmBleService.setVerbose(false);

            // The shared BLE service is now connected. Can be used by the watch.
            vsmDevice.setBleService(vsmBleService);

            logger.info("Biovotion VSM initialize BLE service");
            if (!vsmBleService.initialize(context.getApplicationContext()))
                logger.error("Biovotion VSM unable to initialize BLE service");

            // Automatically connects to the device upon successful start-up initialization
            String id = vsmDevice.descriptor().address();
            logger.info("Biovotion VSM Connecting to {} from activity {}", vsmDevice.descriptor(), this.toString());
            vsmBleService.connect(id, VsmConstants.BLE_CONN_TIMEOUT_MS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vsmBleService = null;
        }
    };



    public BiovotionDeviceManager(Context context, DeviceStatusListener biovotionService, String groupId, TableDataHandler handler, BiovotionTopics topics) {
        this.dataHandler = handler;
        this.bpwTable = dataHandler.getCache(topics.getBloodPulseWaveTopic());
        this.spo2Table = dataHandler.getCache(topics.getSpO2Topic());
        this.hrTable = dataHandler.getCache(topics.getHeartRateTopic());
        this.hrvTable = dataHandler.getCache(topics.getHrvTopic());
        this.rrTable = dataHandler.getCache(topics.getRrTopic());
        this.energyTable = dataHandler.getCache(topics.getEnergyTopic());
        this.temperatureTable = dataHandler.getCache(topics.getTemperatureTopic());
        this.gsrTable = dataHandler.getCache(topics.getGsrTopic());
        this.batteryTopic = topics.getBatteryStateTopic();

        this.biovotionService = biovotionService;
        this.context = context;

        this.deviceStatus = new BiovotionDeviceStatus();
        this.deviceStatus.getId().setUserId(groupId);
        this.deviceName = null;
        this.isClosed = true;
        this.acceptableIds = null;
    }


    public void close() {
        synchronized (this) {
            if (this.isClosed) {
                return;
            }
            logger.info("Closing device {}", deviceName);
            this.isClosed = true;
        }
        if (vsmScanner != null && vsmScanner.isScanning()) vsmScanner.stopScanning();
        if (vsmDevice != null && vsmDevice.isConnected()) vsmDevice.disconnect();
        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
    }


    /*
     * DeviceManager interface
     */

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        logger.info("Biovotion VSM searching for device.");

        // Initializes a Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        vsmBluetoothAdapter = bluetoothManager.getAdapter();

        // Create a VSM scanner and register to be notified when VSM devices have been found
        vsmScanner = new VsmScanner(vsmBluetoothAdapter, this);
        vsmScanner.startScanning();

        this.acceptableIds = Strings.containsPatterns(acceptableIds);
        this.isClosed = false;
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

    @Override
    public boolean equals(Object other) {
        return other == this
                || other != null && getClass().equals(other.getClass())
                && deviceStatus.getId().getSourceId() != null
                && deviceStatus.getId().equals(((BiovotionDeviceManager) other).deviceStatus.getId());
    }

    @Override
    public int hashCode() {
        return deviceStatus.getId().hashCode();
    }

    private synchronized void updateStatus(DeviceStatusListener.Status status) {
        this.deviceStatus.setStatus(status);
        this.biovotionService.deviceStatusUpdated(this, status);
    }


    /*
     * VsmDeviceListener interface
     */

    @Override
    public void onVsmDeviceConnected(@NonNull VsmDevice device, boolean ready) {
        if (!ready)
            return;

        logger.info("Biovotion VSM device connected.");

        updateStatus(DeviceStatusListener.Status.CONNECTED);

        vsmDevice = device;

        vsmStreamController = device.streamController();
        vsmStreamController.addListener(this);
    }

    @Override
    public void onVsmDeviceConnecting(@NonNull VsmDevice device) {
        logger.info("Biovotion VSM device connecting.");
        updateStatus(DeviceStatusListener.Status.CONNECTING);
    }

    @Override
    public void onVsmDeviceConnectionError(@NonNull VsmDevice device, VsmConnectionState errorState) {
        logger.error("Biovotion VSM device connection error.");
        vsmStreamController = null;
    }

    @Override
    public void onVsmDeviceDisconnected(@NonNull VsmDevice device, int statusCode) {
        logger.warn("Biovotion VSM device disconnected.");
        updateStatus(DeviceStatusListener.Status.DISCONNECTED);
        vsmStreamController = null;
    }

    @Override
    public void onVsmDeviceReady(@NonNull VsmDevice device) {
        logger.info("Biovotion VSM device ready.");
        updateStatus(DeviceStatusListener.Status.READY);
        device.connect(5000);
    }


    /*
     * VsmDiscoveryListener interface
     */

    @Override
    public void onVsmDeviceFound(@NonNull Scanner scanner, @NonNull VsmDescriptor descriptor) {
        logger.info("Biovotion VSM device found.");
        vsmScanner.stopScanning();

        if (acceptableIds.length > 0
                && !Strings.findAny(acceptableIds, descriptor.name())
                && !Strings.findAny(acceptableIds, descriptor.address())) {
            logger.info("Device {} with ID {} is not listed in acceptable device IDs", descriptor.name(), "");
            biovotionService.deviceFailedToConnect(descriptor.name());
            return;
        }

        this.deviceName = descriptor.name();
        deviceStatus.getId().setSourceId(descriptor.address());
        logger.info("Biovotion VSM device Name: {} ID: {}", this.deviceName, descriptor.address());

        vsmDevice = VsmDevice.sharedInstance();
        vsmDevice.setDescriptor(descriptor);

        // Bind the shared BLE service
        Intent gattServiceIntent = new Intent(context, BleService.class);
        context.bindService(gattServiceIntent, mServiceConnection, context.BIND_AUTO_CREATE);

        vsmDevice.addListener(this);
    }

    @Override
    public void onScanStopped(@NonNull Scanner scanner) {
        logger.info("Biovotion VSM device scan stopped.");
    }

    @Override
    public void onScanError(@NonNull Scanner scanner, @NonNull BleScanException throwable) {
        logger.error("Scanning error. Code: "+ throwable.getReason());
        // TODO: handle error
    }


    /*
     * StreamListener interface
     */

    @Override
    public void onStreamValueReceived(@NonNull final StreamValue unit) {
        logger.info("Biovotion VSM Data recieved: {}", unit.type);

        switch (unit.type) {
            case BatteryState:
                BatteryState battery = (BatteryState) unit.unit;
                logger.info("Biovotion VSM battery state: cap:{} rate:{} voltage:{} state:{}", battery.capacity, battery.chargeRate, battery.voltage/10.0f, battery.state);
                deviceStatus.setBatteryLevel(battery.capacity);
                deviceStatus.setBatteryChargeRate(battery.chargeRate);
                deviceStatus.setBatteryVoltage(battery.voltage);
                deviceStatus.setBatteryStatus(battery.state);

                BiovotionVSMBatteryState value = new BiovotionVSMBatteryState((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getBatteryLevel(), deviceStatus.getBatteryChargeRate(), deviceStatus.getBatteryVoltage(), deviceStatus.getBatteryStatus());

                dataHandler.trySend(batteryTopic, 0L, deviceStatus.getId(), value);
                break;

            case Algo1:
                Algo1 algo1 = (Algo1) unit.unit;
                deviceStatus.setBloodPulseWave(algo1.bloodPulseWave);
                deviceStatus.setBloodPulseWaveQuality(0); // not available in algo1
                deviceStatus.setSpO2(algo1.spO2);
                deviceStatus.setSpO2Quality(algo1.spO2Quality);
                deviceStatus.setHeartRate(algo1.hr);
                deviceStatus.setHeartRateQuality(algo1.hrQuality);

                BiovotionVSMBloodPulseWave bpwValue = new BiovotionVSMBloodPulseWave((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getBloodPulseWave(), deviceStatus.getBloodPulseWaveQuality());
                BiovotionVSMSpO2 spo2Value = new BiovotionVSMSpO2((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getSpO2(), deviceStatus.getSpO2Quality());
                BiovotionVSMHeartRate hrValue = new BiovotionVSMHeartRate((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getHeartRate(), deviceStatus.getHeartRateQuality());

                dataHandler.addMeasurement(bpwTable, deviceStatus.getId(), bpwValue);
                dataHandler.addMeasurement(spo2Table, deviceStatus.getId(), spo2Value);
                dataHandler.addMeasurement(hrTable, deviceStatus.getId(), hrValue);
                break;

            case Algo2:
                Algo2 algo2 = (Algo2) unit.unit;
                deviceStatus.setHrv(algo2.hrv);
                deviceStatus.setHrvQuality(algo2.hrvQuality);
                deviceStatus.setRr(algo2.respirationRate);
                deviceStatus.setRrQuality(algo2.respirationRateQuality);
                deviceStatus.setEnergy(algo2.energy);
                deviceStatus.setEnergyQuality(algo2.energyQuality);

                BiovotionVSMHeartRateVariability hrvValue = new BiovotionVSMHeartRateVariability((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getHrv(), deviceStatus.getHrvQuality());
                BiovotionVSMRespirationRate rrValue = new BiovotionVSMRespirationRate((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getRr(), deviceStatus.getRrQuality());
                BiovotionVSMEnergy energyValue = new BiovotionVSMEnergy((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getEnergy(), deviceStatus.getEnergyQuality());

                dataHandler.addMeasurement(hrvTable, deviceStatus.getId(), hrvValue);
                dataHandler.addMeasurement(rrTable, deviceStatus.getId(), rrValue);
                dataHandler.addMeasurement(energyTable, deviceStatus.getId(), energyValue);
                break;

            case RawBoard:
                RawBoard rawboard = (RawBoard) unit.unit;
                deviceStatus.setTemperature(rawboard.localTemp);
                deviceStatus.setTemperatureObject(rawboard.objectTemp);
                deviceStatus.setTemperatureBaro(rawboard.barometerTemp);
                deviceStatus.setGsrAmplitude(rawboard.gsrAmplitude);
                deviceStatus.setGsrPhase(rawboard.gsrPhase);

                BiovotionVSMTemperature tempValue = new BiovotionVSMTemperature((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getTemperature(), deviceStatus.getTemperatureObject(), deviceStatus.getTemperatureBaro());
                BiovotionVSMGalvanicSkinResponse gsrValue = new BiovotionVSMGalvanicSkinResponse((double) unit.timestamp, System.currentTimeMillis() / 1000d,
                        deviceStatus.getGsrAmplitude(), deviceStatus.getGsrPhase());

                dataHandler.addMeasurement(temperatureTable, deviceStatus.getId(), tempValue);
                dataHandler.addMeasurement(gsrTable, deviceStatus.getId(), gsrValue);
                break;
        }

    }
}
