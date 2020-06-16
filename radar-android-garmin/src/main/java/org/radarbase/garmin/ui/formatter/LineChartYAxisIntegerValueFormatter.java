package org.radarbase.garmin.ui.formatter;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;

/**
 * Created by morajkar on 6/21/2017.
 */

public class LineChartYAxisIntegerValueFormatter implements YAxisValueFormatter {
    @Override
    public String getFormattedValue(float value, YAxis yAxis) {
        return Integer.toString(Math.round(value));
    }
}
