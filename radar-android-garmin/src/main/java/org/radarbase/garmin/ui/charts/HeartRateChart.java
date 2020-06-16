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
 * Represents GH Heart rate line chart
 * Created by morajkar on 7/19/2017.
 */

public class HeartRateChart extends GHLineChart {
    public static final String HR_CHART_ENTRIES = "hrChartEntries";
    public static final String HR_RESTING_CHART_ENTRIES = "hrRestingChartEntries";
    public static final String HR_CHART_LABELS = "hrChartLabels";
    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    private static final int X_MAX_LANDSCAPE = 20;
    private static final int X_MAX_PORTRAIT = 10;

    public HeartRateChart(Context context) {
        super(context);
    }

    public HeartRateChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeartRateChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> entries = null;
        List<Entry> restingEntries = null;

        List<String> labels = null;
        if (savedInstanceState != null) {
            entries = savedInstanceState.getParcelableArrayList(HR_CHART_ENTRIES);
            restingEntries = savedInstanceState.getParcelableArrayList(HR_RESTING_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(HR_CHART_LABELS);
        }

        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.hr_dataSet), ColorTemplate.getHoloBlue());
        LineDataSet restingDataSet = createDataSet(restingEntries, getContext().getString(R.string.hr_resting_dataSet), Color.GREEN);
        createGHChart(labels, Arrays.asList(dataSet, restingDataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime) {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getHeartRate() != null) {
            ChartData current = new ChartData(((RealTimeChartData)data).getHeartRate().getCurrentHeartRate(), updateTime - PROTECTED_StartTime);
            updateDataSet(current, getContext().getString(R.string.hr_dataSet));
            ChartData resting = new ChartData(((RealTimeChartData)data).getHeartRate().getCurrentRestingHeartRate(), updateTime - PROTECTED_StartTime);
            updateDataSet(resting, getContext().getString(R.string.hr_resting_dataSet));
            updateGHChart(current);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(HR_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.hr_dataSet)));
        outState.putParcelableArrayList(HR_RESTING_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.hr_resting_dataSet)));
        outState.putStringArrayList(HR_CHART_LABELS, getLabels());

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
