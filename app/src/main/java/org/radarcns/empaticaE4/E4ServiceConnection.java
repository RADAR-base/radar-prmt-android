package org.radarcns.empaticaE4;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.MeasurementTable;
import org.radarcns.data.Record;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Created by joris on 26/10/2016.
 */
class E4ServiceConnection implements ServiceConnection, E4DeviceStatusListener {
    private final static Logger logger = LoggerFactory.getLogger(E4ServiceConnection.class);
    private final MainActivity mainActivity;
    private E4Service e4Service;
    private E4DeviceManager device;
    final int clsNumber;
    private final E4Topics topics;

    E4ServiceConnection(MainActivity mainActivity, int clsNumber) {
        this.mainActivity = mainActivity;
        this.clsNumber = clsNumber;
        this.e4Service = null;
        try {
            this.topics = E4Topics.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.device = null;
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        // We've bound to the running Service, cast the IBinder and get instance
        E4Service.LocalBinder binder = (E4Service.LocalBinder) service;
        e4Service = binder.getService();
        e4Service.getDataHandler().addStatusListener(mainActivity);
        mainActivity.updateServerStatus(e4Service.getDataHandler().getStatus());
        e4Service.getDataHandler().checkConnection();
        e4Service.addStatusListener(this);
        e4Service.addStatusListener(mainActivity);
        device = e4Service.getDevice();
        if (device != null) {
            deviceStatusUpdated(device, device.getStatus());
            mainActivity.deviceStatusUpdated(device, device.getStatus());
        } else {
            deviceStatusUpdated(null, E4DeviceStatusListener.Status.READY);
            mainActivity.deviceStatusUpdated(null, E4DeviceStatusListener.Status.READY);
        }

        Button showButton = (Button) mainActivity.findViewById(R.id.showButton);
        showButton.setVisibility(View.VISIBLE);
        showButton.setOnClickListener(new View.OnClickListener() {
            final DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            final DecimalFormat singleDecimal = new DecimalFormat("0.0");
            final LinkedList<Record<MeasurementKey, SpecificRecord>> reversedMeasurements = new LinkedList<>();
            final MeasurementTable table = e4Service.getDataHandler().getCache(topics.getInterBeatIntervalTopic());

            public void onClick(View v) {
                reversedMeasurements.clear();
                for (Record<MeasurementKey, SpecificRecord> measurement : table.getMeasurements(25)) {
                    reversedMeasurements.addFirst(measurement);
                }

                if (!reversedMeasurements.isEmpty()) {
                    StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                    for (Record<MeasurementKey, SpecificRecord> measurement : reversedMeasurements) {
                        EmpaticaE4InterBeatInterval ibi = (EmpaticaE4InterBeatInterval) measurement.value;
                        sb.append(timeFormat.format(1000d * ibi.getTime()));
                        sb.append(": ");
                        sb.append(singleDecimal.format(60d / ibi.getInterBeatInterval()));
                        sb.append('\n');
                    }
                    String view = sb.toString();
                    Toast.makeText(mainActivity, view, Toast.LENGTH_LONG).show();
                    logger.info("Data:\n{}", view);
                } else {
                    Toast.makeText(mainActivity, "No heart rate collected yet.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        e4Service = null;
    }

    @Override
    public void deviceStatusUpdated(final E4DeviceManager deviceManager, final E4DeviceStatusListener.Status status) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case CONNECTED:
                        Intent notificationIntent = new Intent(mainActivity.getApplicationContext(), MainActivity.class);
                        PendingIntent pendingIntent = PendingIntent.getActivity(mainActivity.getApplicationContext(), 0, notificationIntent, 0);

                        Notification.Builder notificationBuilder = new Notification.Builder(mainActivity.getApplicationContext());
                        Bitmap largeIcon = BitmapFactory.decodeResource(mainActivity.getResources(),
                                R.mipmap.ic_launcher);
                        notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
                        notificationBuilder.setLargeIcon(largeIcon);
                        notificationBuilder.setTicker(mainActivity.getText(R.string.service_notification_ticker));
                        notificationBuilder.setWhen(System.currentTimeMillis());
                        notificationBuilder.setContentIntent(pendingIntent);
                        notificationBuilder.setContentText(mainActivity.getText(R.string.service_notification_text));
                        notificationBuilder.setContentTitle(mainActivity.getText(R.string.service_notification_title));
                        Notification notification = notificationBuilder.build();

                        e4Service.startBackgroundListener(notification);
                        device = deviceManager;
                        break;
                    case DISCONNECTED:
                        device = e4Service.getDevice();
                        e4Service.stopBackgroundListener();
                        break;
                }
            }
        });
    }

    void disconnect() {
        if (e4Service.isRecording()) {
            e4Service.disconnect();
        }
    }

    @Override
    public void deviceFailedToConnect(String name) {
    }


    Class<? extends E4Service> serviceClass() {
        switch (clsNumber) {
            case 0:
                return E4Service0.class;
            case 1:
                return E4Service1.class;
            case 2:
                return E4Service2.class;
            case 3:
                return E4Service3.class;
            default:
                return null;
        }
    }

    public boolean hasService() {
        return e4Service != null;
    }

    public void close() {
        e4Service.getDataHandler().removeStatusListener(mainActivity);
        e4Service.removeStatusListener(this);
        e4Service = null;
    }
}
