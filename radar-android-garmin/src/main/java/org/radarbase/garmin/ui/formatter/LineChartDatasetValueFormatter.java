package org.radarbase.garmin.ui.formatter;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * Created by morajkar on 6/21/2017.
 */

public class LineChartDatasetValueFormatter implements ValueFormatter {
    @Override
    public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
        return Integer.toString(Math.round(value));
    }
}
