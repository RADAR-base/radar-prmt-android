package org.radarcns.empaticaE4;

import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.widget.Toast;

import org.radarcns.android.DeviceServiceConnection;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Shows recently collected heartbeats in a Toast.
 */
class E4HeartbeatToast extends AsyncTask<DeviceServiceConnection, Void, String[]> {
    private final Context context;
    final static DateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    final static DecimalFormat singleDecimal = new DecimalFormat("0.0");
    final static AvroTopic<MeasurementKey, EmpaticaE4InterBeatInterval> topic = E4Topics.getInstance().getInterBeatIntervalTopic();

    E4HeartbeatToast(Context context) {
        this.context = context;
    }

    @Override
    protected String[] doInBackground(DeviceServiceConnection... params) {
        String[] results = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            try {
                List<Record<MeasurementKey, EmpaticaE4InterBeatInterval>> measurements = params[i].getRecords(topic, 25);
                if (!measurements.isEmpty()) {
                    StringBuilder sb = new StringBuilder(3200); // <32 chars * 100 measurements
                    for (Record<MeasurementKey, EmpaticaE4InterBeatInterval> measurement : measurements) {
                        sb.append(timeFormat.format(1000d * measurement.value.getTime()));
                        sb.append(": ");
                        sb.append(singleDecimal.format(60d / measurement.value.getInterBeatInterval()));
                        sb.append('\n');
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
                Toast.makeText(context, "No heart rate collected yet.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
            }
        }
    }
}
