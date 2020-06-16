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
 * Represents GH floor line chart
 * Created by morajkar on 7/20/2017.
 */

public class FloorsChart extends GHLineChart {
    public static final String FLOORS_CLIMBED_CHART_ENTRIES = "floorsClimbedChartEntries";
    public static final String FLOORS_DESCENDED_CHART_ENTRIES = "floorsDescendedChartEntries";
    public static final String FLOORS_CHART_LABELS = "floorChartLabels";

    public FloorsChart(Context context) {
        super(context);
    }

    public FloorsChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloorsChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> climbedEntries = null;
        List<Entry> descendedEntries = null;
        List<String> labels = null;
        if (savedInstanceState != null) {
            climbedEntries = savedInstanceState.getParcelableArrayList(FLOORS_CLIMBED_CHART_ENTRIES);
            descendedEntries = savedInstanceState.getParcelableArrayList(FLOORS_DESCENDED_CHART_ENTRIES);
            labels = savedInstanceState.getStringArrayList(FLOORS_CHART_LABELS);
        }
        LineDataSet climbedDataSet = createDataSet(climbedEntries, getContext().getString(R.string.floors_climbed_dataSet), ColorTemplate.getHoloBlue());
        LineDataSet descendedDataSet = createDataSet(descendedEntries,getContext().getString(R.string.floors_descended_dataSet), Color.GREEN);
        createGHChart(labels, Arrays.asList(climbedDataSet, descendedDataSet), appStartTime);
        startChartYAxisAtZero(true);
    }

    @Override
    public void updateChart(ChartData data, long updateTime) {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getAscent() != null) {
            ChartData climbed = new ChartData(((RealTimeChartData)data).getAscent().getCurrentMetersClimbed(), updateTime - PROTECTED_StartTime);
            updateDataSet(climbed, getContext().getString(R.string.floors_climbed_dataSet));
            ChartData descended = new ChartData(((RealTimeChartData)data).getAscent().getCurrentMetersDescended(), updateTime - PROTECTED_StartTime);
            updateDataSet(descended, getContext().getString(R.string.floors_descended_dataSet));
            updateGHChart(descended);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(FLOORS_CLIMBED_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.floors_climbed_dataSet)));
        outState.putParcelableArrayList(FLOORS_DESCENDED_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.floors_descended_dataSet)));
        outState.putStringArrayList(FLOORS_CHART_LABELS, getLabels());
    }
}
