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
 * Represents GH calories line chart
 * Created by morajkar on 7/20/2017.
 */

public class CaloriesChart extends GHLineChart {
    public static final String ACTIVE_CALORIES_CHART_ENTRIES = "activeCaloriesChartEntries";
    public static final String TOTAL_CALORIES_CHART_ENTRIES = "totalCaloriesChartEntries";
    public static final String CALORIES_CHART_LABELS = "caloriesChartLabels";

    public CaloriesChart(Context context) {
        super(context);
    }

    public CaloriesChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaloriesChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> activeEntries = null;
        List<Entry> totalEntries = null;
        List<String> labels = null;
        if (savedInstanceState != null) {
            activeEntries = savedInstanceState.getParcelableArrayList(ACTIVE_CALORIES_CHART_ENTRIES);
            totalEntries = savedInstanceState.getParcelableArrayList(TOTAL_CALORIES_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(CALORIES_CHART_LABELS);
        }
        LineDataSet activeDataSet = createDataSet(activeEntries, getContext().getString(R.string.active_calories_dataSet), ColorTemplate.getHoloBlue());
        LineDataSet totalDataSet = createDataSet(totalEntries, getContext().getString(R.string.total_calories_dataSet), Color.GREEN);
        createGHChart(labels, Arrays.asList(activeDataSet, totalDataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getCalories() != null)
        {
            ChartData total = new ChartData(((RealTimeChartData)data).getCalories().getCurrentTotalCalories(), updateTime - PROTECTED_StartTime);
            updateDataSet(total, getContext().getString(R.string.total_calories_dataSet));
            ChartData active = new ChartData(((RealTimeChartData)data).getCalories().getCurrentActiveCalories(), updateTime - PROTECTED_StartTime);
            updateDataSet(active, getContext().getString(R.string.active_calories_dataSet));
            updateGHChart(active);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(ACTIVE_CALORIES_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.active_calories_dataSet)));
        outState.putParcelableArrayList(TOTAL_CALORIES_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.total_calories_dataSet)));
        outState.putStringArrayList(CALORIES_CHART_LABELS, getLabels());
    }
}
