package org.radarbase.garmin.ui.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.radarbase.garmin.ui.formatter.LineChartDatasetValueFormatter;
import org.radarbase.garmin.ui.formatter.LineChartXAxisIntegerValueFormatter;
import org.radarbase.garmin.ui.formatter.LineChartYAxisIntegerValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
/**
 * Parent class for all GH line charts
 * Created by morajkar on 7/19/2017.
 */

public abstract class GHLineChart extends LineChart
{
    private static final int MAX_VISIBLE_VALUE_COUNT = 30;
    private static final int MAX_X_DATA_COUNT = 1000;
    private static final int X_MAX_LANDSCAPE = 20;
    private static final int X_MAX_PORTRAIT = 10;

    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    protected long PROTECTED_StartTime;
    protected long lastSyncTime;

    public GHLineChart(Context context) {
        super(context);
    }

    public GHLineChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GHLineChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Creates a Garmin Health Chart. It checks for the saved parcel data before creating the chart.
     * @param savedInstanceState
     * @param appStartTime
     */
    public abstract void createChart(Bundle savedInstanceState, long appStartTime);

    /**
     * Creates a Garmin Health Chart. It checks for the saved parcel data before creating the chart.
     * @param savedInstanceState
     */
    public void createChart(Bundle savedInstanceState)
    {
        createChart(savedInstanceState, -1);
    }

    /**
     * Updates chart with the latest data. For multiple datasets, it is preferred to create datasets before making call to this method.
     * @param result
     * @param updateTime
     */
    public abstract void updateChart(ChartData result, long updateTime);

    /**
     * Saves the state of the chart in bundle
     * @param outState
     */
    public abstract void saveChartState(Bundle outState);

    /**
     * This is the max x-axis value count, after which, chart cleaning begins.
     * A chart can override this method to have different max count.
     * @return
     */
    protected int getXAxisMax(){
        return MAX_X_DATA_COUNT;
    }
    /**
     * Creates a line chart with the x-axis labels and datasets.
     * @param labels
     * @param dataSets
     * @param appStartTime
     */
    protected void createGHChart(List<String> labels, List<LineDataSet> dataSets, long appStartTime) {
        if(labels == null){
            labels = new ArrayList<>();
        }
        LineData lineData = new LineData(labels, dataSets);
        setData(lineData);
        setDescription("");
        setMaxVisibleValueCount(MAX_VISIBLE_VALUE_COUNT);
        setChartProperties();
        setVisibleXRangeMaximum(determineChartXVisibleRange());
        this.PROTECTED_StartTime = appStartTime;
    }

    protected void createGHChart(List<String> labels, List<LineDataSet> dataSets, long appStartTime, int xVisibleCount) {
        if(labels == null){
            labels = new ArrayList<>();
        }
        LineData lineData = new LineData(labels, dataSets);
        setData(lineData);
        setDescription("");
        setMaxVisibleValueCount(getChartXVisibleRange());
        YAxis yAxisLeft = getAxisLeft();
        yAxisLeft.setAxisMinValue(0);
        setChartProperties();
        setVisibleXRangeMaximum(xVisibleCount);
        this.PROTECTED_StartTime = appStartTime;
    }

    /**
     * Creates a dataset for linechart with consistent properties.
     * @param entries
     * @param dataSetName
     * @param color
     * @return
     */
    protected LineDataSet createDataSet(List<Entry> entries, String dataSetName, int color){
        if(entries == null){
            entries = new ArrayList<>();
        }
        LineDataSet dataSet = new LineDataSet(entries, dataSetName);
        dataSet.setDrawCubic(true);
        dataSet.setCubicIntensity(0.2f);
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleSize(1f);
        dataSet.setFillAlpha(65);
        dataSet.setFillColor(ColorTemplate.getHoloBlue());
        dataSet.setHighLightColor(Color.rgb(244,117,177));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);

        return dataSet;
    }
    /**+
     * Sets up common chart properties
     */
    protected void setChartProperties() {
        // sets chart properties
        setHighlightPerDragEnabled(true);
        setTouchEnabled(true);
        setDragEnabled(true);
        setScaleEnabled(true);
        setDrawGridBackground(false);
        setPinchZoom(true);

        // sets legend
        Legend legend = getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextColor(Color.BLACK);

        // x-axis properties
        XAxis xAxis = getXAxis();
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawGridLines(false);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setValueFormatter(new LineChartXAxisIntegerValueFormatter());
        // xAxis.setDrawLabels(false);

        //y-axis properties
        YAxis yAxisLeft = getAxisLeft();
        yAxisLeft.setTextColor(Color.BLACK);
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setStartAtZero(false);
        yAxisLeft.setValueFormatter(new LineChartYAxisIntegerValueFormatter());

        YAxis yAxisRight = getAxisRight();
        yAxisRight.setEnabled(false);

        // apply the formatter for all datasets
        for (LineDataSet dataSet : getData().getDataSets()) {
            dataSet.setValueFormatter(new LineChartDatasetValueFormatter());
        }
    }
    /**
     * Updates the chart with the latest data
     * @param result
     */
    protected void updateGHChart(ChartData result)
    {
        if(result != null && getData() != null && getData().getDataSetByIndex(0) != null && !getFormattedXValue(result.getTimestamp()).contentEquals(getFormattedXValue(lastSyncTime)))
        {
            cleanChart();
            getLineData().getXVals().add(getFormattedXValue(result.getTimestamp()));
            getData().notifyDataChanged();
            notifyDataSetChanged();
            postInvalidate();
            moveViewToX(getData().getDataSetByIndex(0).getEntryCount());
            setVisibleXRangeMaximum(getChartXVisibleRange());
            lastSyncTime = result.getTimestamp();
        }
    }

    /**
     * Updates the database with the latest data
     * @param result
     * @param dataSetName
     */
    public void updateDataSet(ChartData result, String dataSetName)
    {
        if(result != null && !getFormattedXValue(result.getTimestamp()).contentEquals(getFormattedXValue(lastSyncTime)))
        {
            DataSet dataset = getData().getDataSetByLabel(dataSetName, true);

            if(dataset != null)
            {
                dataset.addEntry(new Entry(result.getDataPoint(), dataset.getEntryCount()));
            }
        }
    }

    /**
     * Removes old values and manages the chart memory
     */
    protected void cleanChart(){
        if(getData() != null && getData().getDataSetByIndex(0) != null)
        {
            if(getData().getDataSetByIndex(0).getEntryCount() > getXAxisMax())
            {
                // remove the first entry from each dataset and reset the index
                for (LineDataSet dataSet : getData().getDataSets()) {
                    dataSet.removeEntry(0);
                    for (Entry entry : dataSet.getYVals())
                    {
                        entry.setXIndex(entry.getXIndex() - 1);
                    }
                }
                // remove the x-value
                getData().removeXValue(0);
            }
        }
    }
    /**
     * Enables/disables values displayed on the dataset
     * @param drawValues
     */
    protected void drawDataSetValues(boolean drawValues)
    {
        if(getData() != null && getData().getDataSets() != null){
            for (LineDataSet dataSet : getData().getDataSets()) {
                dataSet.setDrawValues(drawValues);
            }
        }
    }
    /**
     * Returns a list of labels used in the chart
     * @return
     */
    protected ArrayList<String> getLabels() {
        ArrayList<String> labels = null;
        if(getData() != null){
            labels = (ArrayList<String>)getData().getXVals();
        }
        return labels;
    }

    /**
     * Returns chart entries
     * @param dataSetName
     * @return
     */
    protected ArrayList<Entry> getChartEntries(String dataSetName) {
        ArrayList<Entry> entries = null;
        if(getData() != null){
            DataSet dataset = getData().getDataSetByLabel(dataSetName, true);
            if(dataset != null){
                entries = (ArrayList<Entry>)dataset.getYVals();
            }
        }
        return entries;
    }

    /**
     * Sets if the chart's Y axis starts at zero or not. By default, health charts do not have Y axis begin at zero.
     * @param startAtZero
     */
    protected void startChartYAxisAtZero(boolean startAtZero){
        YAxis yAxisLeft = getAxisLeft();
        yAxisLeft.setStartAtZero(startAtZero);
    }

    /**
     * Sets if the chart's Y axis starts at zero or not. By default, health charts do not have Y axis begin at zero.
     */
    protected void setChartYAxisMax(int max){
        YAxis yAxisLeft = getAxisLeft();
        yAxisLeft.setAxisMaxValue(max);
    }

    /**
     * Sets if the chart's Y axis starts at zero or not. By default, health charts do not have Y axis begin at zero.
     */
    protected void setChartYAxis(int min, int max) {
        YAxis yAxisLeft = getAxisLeft();
        yAxisLeft.setAxisMinValue(min);
        yAxisLeft.setAxisMaxValue(max);
    }

    /**
     * Determines number of x-values based on the screen-orientation.
     */
    private int determineChartXVisibleRange()
    {
        int xCount = getXAxisLandscapeMax();
        int displayMode = getResources().getConfiguration().orientation;
        if(displayMode == Configuration.ORIENTATION_PORTRAIT){
            xCount = getXAxisPortraitMax();
        }
        maxXVisibleCount = xCount;
        return xCount;
    }

    /**
     * Override this method to provide a different x-axis max for the landscape orientation
     * @return
     */
    protected int getXAxisLandscapeMax(){
        return X_MAX_LANDSCAPE;
    }

    /**
     * Override this method to provide a different x-axis max for the portrait orientation
     * @return
     */
    protected  int getXAxisPortraitMax(){
        return X_MAX_PORTRAIT;
    }

    /**
     * Override this method to provide a different x-axis visible range
     * @return
     */
    protected int getChartXVisibleRange(){
        return maxXVisibleCount;
    }

    public long getStartTime() {
        return PROTECTED_StartTime;
    }

    public void setStartTime(long startTime) {
        this.PROTECTED_StartTime = startTime;
    }

    /**
     * Returns a formatted x-value. The format used is mm:ss
     * @param timestamp
     * @return
     */
    @SuppressLint("DefaultLocale")
    private String getFormattedXValue(long timestamp)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        return String.format("%02d:%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
    }
}
