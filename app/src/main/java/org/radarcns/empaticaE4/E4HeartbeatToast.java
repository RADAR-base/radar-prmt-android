package org.radarcns.empaticaE4;

import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.widget.Toast;

import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.data.Record;
import org.radarcns.empatica.EmpaticaE4InterBeatInterval;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Boast;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Shows recently collected heartbeats in a Toast.
 */
public class E4HeartbeatToast extends
        AsyncTask<DeviceServiceConnection<E4DeviceStatus>, Void, String[]> {
    private final Context context;
    private static final DecimalFormat singleDecimal = new DecimalFormat("0.0");
    private static final AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> topic = E4Topics
            .getInstance().getInterBeatIntervalTopic();

    public E4HeartbeatToast(Context context) {
        this.context = context;
    }

    @Override
    @SafeVarargs
    protected final String[] doInBackground(DeviceServiceConnection<E4DeviceStatus>... params) {
        String[] results = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            try {
                results[i] = doOne(params[i], 2);
            } catch (RemoteException | IOException e) {
                results[i] = null;
            }
        }
        return results;
    }

    private String doOne(DeviceServiceConnection<E4DeviceStatus> param, int numRecords)
            throws IOException, RemoteException {
        List<Record<MeasurementKey, EmpaticaE4InterBeatInterval>> measurements = param
                .getRecords(topic, numRecords);

        if (!measurements.isEmpty()) {
            StringBuilder sb = new StringBuilder(numRecords * 32);
            for (Record<MeasurementKey, EmpaticaE4InterBeatInterval> measurement : measurements) {
                long timeMs = Math.round(1000d * measurement.value.getTimeReceived());
                double diffTime = (System.currentTimeMillis() - timeMs) / 1000d;
                sb.append(singleDecimal.format(diffTime));
                sb.append(" sec. ago: ");
                sb.append(singleDecimal.format(60d / measurement.value.getInterBeatInterval()));
                sb.append(" bpm\n");
            }
            return sb.toString();
        } else {
            return null;
        }
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
