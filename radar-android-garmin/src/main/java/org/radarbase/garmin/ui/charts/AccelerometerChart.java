package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;

import com.garmin.device.realtime.AccelerometerSample;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.radarbase.garmin.R;

import java.util.Arrays;
import java.util.List;

/**
 * Represents accelerometer charts
 * Created by morajkar on 8/2/2017.
 */

public class AccelerometerChart extends GHLineChart {
    public static final String ACCEL_X_CHART_ENTRIES = "accelXChartEntries";
    public static final String ACCEL_Y_CHART_ENTRIES = "accelYChartEntries";
    public static final String ACCEL_Z_CHART_ENTRIES = "accelZChartEntries";
    public static final String ACCEL_CHART_LABELS = "accelChartLabels";
    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    private static final int X_MAX_LANDSCAPE = 400;
    private static final int X_MAX_PORTRAIT = 200;

    public AccelerometerChart(Context context) {
        super(context);
    }

    public AccelerometerChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccelerometerChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime)
    {
        List<Entry> xEntries = null;
        List<Entry> yEntries = null;
        List<Entry> zEntries = null;
        List<String> labels = null;

        if(savedInstanceState != null)
        {
            xEntries = savedInstanceState.getParcelableArrayList(ACCEL_X_CHART_ENTRIES);
            yEntries = savedInstanceState.getParcelableArrayList(ACCEL_Y_CHART_ENTRIES);
            zEntries = savedInstanceState.getParcelableArrayList(ACCEL_Z_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(ACCEL_CHART_LABELS);
        }

        LineDataSet xDataset = createDataSet(xEntries, getContext().getString(R.string.x_axis_dataset), ColorTemplate.getHoloBlue());
        LineDataSet yDataset = createDataSet(yEntries, getContext().getString(R.string.y_axis_dataset), Color.GREEN);
        LineDataSet zDataset = createDataSet(zEntries, getContext().getString(R.string.z_axis_dataset), Color.CYAN);

        createGHChart(labels, Arrays.asList(xDataset, yDataset, zDataset), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getAccelerometer() != null)
        {
            for(AccelerometerSample sample : ((RealTimeChartData)data).getAccelerometer().getAccelerometerSamples())
            {
                ChartData xData = new ChartData(sample.getX(), updateTime - PROTECTED_StartTime);
                updateDataSet(xData, getContext().getString(R.string.x_axis_dataset));
                ChartData yData = new ChartData(sample.getY(), updateTime - PROTECTED_StartTime);
                updateDataSet(yData, getContext().getString(R.string.y_axis_dataset));
                ChartData zData = new ChartData(sample.getZ(), updateTime - PROTECTED_StartTime);
                updateDataSet(zData, getContext().getString(R.string.z_axis_dataset));
                updateGHChart(zData);
            }
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(ACCEL_X_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.x_axis_dataset)));
        outState.putParcelableArrayList(ACCEL_Y_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.y_axis_dataset)));
        outState.putParcelableArrayList(ACCEL_Z_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.z_axis_dataset)));
        outState.putStringArrayList(ACCEL_CHART_LABELS, getLabels());
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
