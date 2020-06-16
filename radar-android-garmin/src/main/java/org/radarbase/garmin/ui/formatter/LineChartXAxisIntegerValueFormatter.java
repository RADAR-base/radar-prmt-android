package org.radarbase.garmin.ui.formatter;

import com.github.mikephil.charting.formatter.XAxisValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.concurrent.TimeUnit;

/**
 * Created by morajkar on 6/29/2017.
 */

public class LineChartXAxisIntegerValueFormatter implements XAxisValueFormatter {
    @Override
    public String getXValue(String original, int index, ViewPortHandler viewPortHandler) {
        String xValue = "";
        try {
            if (original != null && !original.isEmpty()) {
                long timestamp = Long.valueOf(original);
                xValue = String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(timestamp),
                        TimeUnit.MILLISECONDS.toSeconds(timestamp) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timestamp)));
            }
        } catch (Exception e) {
            // catching parsing errors. Return empty string.
        }
        // TODO: CHECK LATER
        return original;
    }
}
