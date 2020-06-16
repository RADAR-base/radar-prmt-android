package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.radarbase.garmin.R;

import java.util.Collections;
import java.util.List;

/**
 * Represents accelerometer charts
 * Created by morajkar on 8/2/2017.
 */

public class Spo2Chart extends GHLineChart
{
    public static final String SPO2_CHART_ENTRIES = "spo2ChartEntries";
    public static final String SPO2_CHART_LABELS = "spo2ChartLabels";

    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    private static final int X_MAX_LANDSCAPE = 400;
    private static final int X_MAX_PORTRAIT = 200;

    public Spo2Chart(Context context)
    {
        super(context);
    }

    public Spo2Chart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public Spo2Chart(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime)
    {
        List<Entry> entries = null;
        List<String> labels = null;

        if (savedInstanceState != null)
        {
            entries = savedInstanceState.getParcelableArrayList(SPO2_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(SPO2_CHART_LABELS);
        }

        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.spo2_dataSet), ColorTemplate.getHoloBlue());
        createGHChart(labels, Collections.singletonList(dataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getSpo2() != null)
        {
            ChartData result = new ChartData(((RealTimeChartData)data).getSpo2().getSpo2Reading(), updateTime - PROTECTED_StartTime);
            updateDataSet(result, getContext().getString(R.string.spo2_dataSet));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(SPO2_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.spo2_dataSet)));
        outState.putStringArrayList(SPO2_CHART_LABELS, getLabels());
    }

    protected int getXAxisLandscapeMax()
    {
        return X_MAX_LANDSCAPE;
    }

    protected  int getXAxisPortraitMax()
    {
        return X_MAX_PORTRAIT;
    }

    protected int getChartXVisibleRange()
    {
        return maxXVisibleCount;
    }
}
