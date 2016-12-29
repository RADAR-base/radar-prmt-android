package org.radarcns.empaticaE4;

import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.widget.Toast;

import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Boast;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Shows recently collected heartbeats in a Toast.
 */
public class E4HeartbeatToast extends AsyncTask<DeviceServiceConnection<E4DeviceStatus>, Void, String[]> {
    private final Context context;
    static final DecimalFormat singleDecimal = new DecimalFormat("0.0");
    static final AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> topic = E4Topics.getInstance().getInterBeatIntervalTopic();

    public E4HeartbeatToast(Context context) {
        this.context = context;
    }

    @Override
    @SafeVarargs
    protected final String[] doInBackground(DeviceServiceConnection<E4DeviceStatus>... params) {
        String[] results = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            try {
                List<Record<MeasurementKey, EmpaticaE4InterBeatInterval>> measurements = params[i].getRecords(topic, 2);
                if (!measurements.isEmpty()) {
                    StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                    for (Record<MeasurementKey, EmpaticaE4InterBeatInterval> measurement : measurements) {
                        long diffTimeMillis = System.currentTimeMillis() - (long) (1000d * measurement.value.getTimeReceived());
                        sb.append(singleDecimal.format(diffTimeMillis / 1000d));
                        sb.append(" sec. ago: ");
                        sb.append(singleDecimal.format(60d / measurement.value.getInterBeatInterval()));
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
