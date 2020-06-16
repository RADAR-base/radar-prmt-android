package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import org.radarbase.garmin.R;

import java.util.Arrays;
import java.util.List;

/**
 * Represents GH intensity minutes line chart
 * Created by morajkar on 7/20/2017.
 */

public class IntensityMinutesChart extends GHLineChart {
    public static final String MODERATE_INTENSITY_MINUTES_CHART_ENTRIES = "moderateIntensityMinutesChartEntries";
    public static final String VIGOROUS_INTENSITY_MINUTES_CHART_ENTRIES = "vigorousIntensityMinutesChartEntries";
    public static final String INTENSITY_MINUTES_CHART_LABELS = "floorChartLabels";

    public IntensityMinutesChart(Context context) {
        super(context);
    }

    public IntensityMinutesChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntensityMinutesChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> moderateEntries = null;
        List<Entry> vigorousEntries = null;
        List<String> labels = null;
        LineDataSet moderateDataSet = createDataSet(moderateEntries, getContext().getString(R.string.moderate_intensity_minutes_dataSet), ColorTemplate.getHoloBlue());
        LineDataSet vigorousDataSet = createDataSet(vigorousEntries, getContext().getString(R.string.vigorous_intensity_minutes_dataSet), Color.GREEN);
        createGHChart(labels, Arrays.asList(moderateDataSet, vigorousDataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getIntensityMinutes() != null) {
            ChartData moderateData = new ChartData(((RealTimeChartData)data).getIntensityMinutes().getDailyModerateMinutes(), updateTime - PROTECTED_StartTime);
            updateDataSet(moderateData, getContext().getString(R.string.moderate_intensity_minutes_dataSet));

            ChartData vigorousData = new ChartData(((RealTimeChartData)data).getIntensityMinutes().getDailyVigorousMinutes(), updateTime - PROTECTED_StartTime);
            updateDataSet(vigorousData, getContext().getString(R.string.vigorous_intensity_minutes_dataSet));

            updateGHChart(vigorousData);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(MODERATE_INTENSITY_MINUTES_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.moderate_intensity_minutes_dataSet)));
        outState.putParcelableArrayList(VIGOROUS_INTENSITY_MINUTES_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.vigorous_intensity_minutes_dataSet)));
        outState.putStringArrayList(INTENSITY_MINUTES_CHART_LABELS, getLabels());
    }
}
