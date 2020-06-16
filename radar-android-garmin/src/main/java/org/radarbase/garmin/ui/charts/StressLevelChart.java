package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.radarbase.garmin.R;

import java.util.Arrays;
import java.util.List;


/**
 * Represents GH stress level line chart
 * Created by morajkar on 7/20/2017.
 */

public class StressLevelChart extends GHLineChart
{
    public static final String STRESS_CHART_ENTRIES = "stressChartEntries";
    public static final String STRESS_CHART_LABELS = "stressChartLabels";

    public StressLevelChart(Context context) {
        super(context);
    }

    public StressLevelChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StressLevelChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime)
    {
        List<Entry> entries = null;
        List<String> labels = null;

        if (savedInstanceState != null)
        {
            entries = savedInstanceState.getParcelableArrayList(STRESS_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(STRESS_CHART_LABELS);
        }
        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.stress_dataSet), ColorTemplate.getHoloBlue());
        createGHChart(labels, Arrays.asList(dataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getStress() != null)
        {
            ChartData result = new ChartData(((RealTimeChartData)data).getStress().getStressScore(), updateTime - PROTECTED_StartTime);
            updateDataSet(result, getContext().getString(R.string.stress_dataSet));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(STRESS_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.stress_dataSet)));
        outState.putStringArrayList(STRESS_CHART_LABELS, getLabels());
    }
}
