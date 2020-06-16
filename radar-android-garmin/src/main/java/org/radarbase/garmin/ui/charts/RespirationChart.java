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

public class RespirationChart extends GHLineChart
{
    public static final String RESPIRATION_CHART_ENTRIES = "respirationChartEntries";
    public static final String RESPIRATION_CHART_LABELS = "respirationChartLabels";

    private int maxXVisibleCount = X_MAX_LANDSCAPE;
    private static final int X_MAX_LANDSCAPE = 400;
    private static final int X_MAX_PORTRAIT = 200;

    public RespirationChart(Context context)
    {
        super(context);
    }

    public RespirationChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public RespirationChart(Context context, AttributeSet attrs, int defStyle)
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
            entries = savedInstanceState.getParcelableArrayList(RESPIRATION_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(RESPIRATION_CHART_LABELS);
        }

        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.respiration_dataSet), ColorTemplate.getHoloBlue());
        createGHChart(labels, Collections.singletonList(dataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime)
    {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getRespiration() != null)
        {
            ChartData result = new ChartData(((RealTimeChartData)data).getRespiration().getRespirationRate(), updateTime - PROTECTED_StartTime);
            updateDataSet(result, getContext().getString(R.string.respiration_dataSet));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(RESPIRATION_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.respiration_dataSet)));
        outState.putStringArrayList(RESPIRATION_CHART_LABELS, getLabels());
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
