package org.radarcns.pebble2;

import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.widget.Toast;

import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.pebble.Pebble2HeartRateFiltered;
import org.radarcns.util.Boast;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Shows recently collected heartbeats in a Toast.
 */
public class Pebble2HeartbeatToast extends AsyncTask<DeviceServiceConnection<Pebble2DeviceStatus>, Void, String[]> {
    private final Context context;
    private static final DecimalFormat singleDecimal = new DecimalFormat("0.0");
    private static final AvroTopic<MeasurementKey, Pebble2HeartRateFiltered> topic = Pebble2Topics
            .getInstance().getHeartRateFilteredTopic();

    public Pebble2HeartbeatToast(Context context) {
        this.context = context;
    }

    @Override
    @SafeVarargs
    protected final String[] doInBackground(DeviceServiceConnection<Pebble2DeviceStatus>... params) {
        String[] results = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            try {
                List<Record<MeasurementKey, Pebble2HeartRateFiltered>> measurements = params[i].getRecords(topic, 25);
                if (!measurements.isEmpty()) {
                    StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                    for (Record<MeasurementKey, Pebble2HeartRateFiltered> measurement : measurements) {
                        long diffTimeMillis = System.currentTimeMillis() - (long) (1000d * measurement.value.getTimeReceived());
                        sb.append(singleDecimal.format(diffTimeMillis / 1000d));
                        sb.append(" sec. ago: ");
                        sb.append(singleDecimal.format(measurement.value.getHeartRate()));
                        sb.append(" bpm\n");
                    }
                    results[i] = sb.toString();
                } else {
                    results[i] = null;
                }
            } catch (RemoteException | IOException e) {
                results[i] = null;
            }
        }
        return results;
    }

    @Override
    protected void onPostExecute(String[] strings) {
        for (String s : strings) {
            if (s == null) {
                Boast.makeText(context, "No heart rate collected yet.", Toast.LENGTH_SHORT).show();
            } else {
                Boast.makeText(context, s, Toast.LENGTH_LONG).show();
            }
        }
    }
}
