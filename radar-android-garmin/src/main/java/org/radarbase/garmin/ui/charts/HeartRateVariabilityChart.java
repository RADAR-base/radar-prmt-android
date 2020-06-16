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
 * Represents GH heart rate variability line chart
 * Created by morajkar on 7/20/2017.
 */

public class HeartRateVariabilityChart extends GHLineChart {
    public static final String HRV_CHART_ENTRIES = "hrvChartEntries";
    public static final String HRV_CHART_LABELS = "hrvChartLabels";
    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    private static final int X_MAX_LANDSCAPE = 400;
    private static final int X_MAX_PORTRAIT = 200;

    public HeartRateVariabilityChart(Context context) {
        super(context);
    }

    public HeartRateVariabilityChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeartRateVariabilityChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> entries = null;
        List<String> labels = null;
        if (savedInstanceState != null) {
            entries = savedInstanceState.getParcelableArrayList(HRV_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(HRV_CHART_LABELS);
        }
        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.hrv_dataSet), ColorTemplate.getHoloBlue());
        createGHChart(labels, Arrays.asList(dataSet), appStartTime);
        drawDataSetValues(false);
    }

    @Override
    public void updateChart(ChartData data, long updateTime) {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getHeartRateVariability() != null) {
            ChartData result = new ChartData(((RealTimeChartData)data).getHeartRateVariability().getHeartRateVariability(), updateTime - PROTECTED_StartTime);
            updateDataSet(result, getContext().getString(R.string.hrv_dataSet));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(HRV_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.hrv_dataSet)));
        outState.putStringArrayList(HRV_CHART_LABELS, getLabels());
    }
    protected int getXAxisLandscapeMax(){
        return X_MAX_LANDSCAPE;
    }
    protected  int getXAxisPortraitMax(){
        return X_MAX_PORTRAIT;
    }
    protected int getChartXVisibleRange(){
        return maxXVisibleCount;
    }
}
