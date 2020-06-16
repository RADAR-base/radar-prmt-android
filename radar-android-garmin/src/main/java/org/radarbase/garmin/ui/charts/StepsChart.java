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
 * Represents GH steps line chart
 * Created by morajkar on 7/20/2017.
 */

public class StepsChart extends GHLineChart {
    public static final String STEPS_CHART_ENTRIES = "stepsChartEntries";
    public static final String STEPS_CHART_GOAL_ENTRIES = "stepsChartGoalEntries";
    public static final String STEPS_CHART_LABELS = "stepsChartLabels";

    public StepsChart(Context context) {
        super(context);
    }

    public StepsChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StepsChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime) {
        List<Entry> entries = null;
        List<Entry> goalEntries = null;
        List<String> labels = null;
        if (savedInstanceState != null) {
            entries = savedInstanceState.getParcelableArrayList(STEPS_CHART_ENTRIES);
            goalEntries = savedInstanceState.getParcelableArrayList(STEPS_CHART_GOAL_ENTRIES);
            labels = savedInstanceState.getStringArrayList(STEPS_CHART_LABELS);
        }
        LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.steps_dataSet), ColorTemplate.getHoloBlue());
        LineDataSet goalDataSet = createDataSet(goalEntries, getContext().getString(R.string.steps_goal_dataSet), Color.RED);
        createGHChart(labels, Arrays.asList(dataSet, goalDataSet), appStartTime);
    }

    @Override
    public void updateChart(ChartData data, long updateTime) {
        if(data != null && data instanceof RealTimeChartData && ((RealTimeChartData)data).getSteps() != null){
            ChartData result = new ChartData(((RealTimeChartData)data).getSteps().getCurrentStepCount(), updateTime - PROTECTED_StartTime);
            updateDataSet(result, getContext().getString(R.string.steps_dataSet));
            ChartData goal = new ChartData(((RealTimeChartData)data).getSteps().getCurrentStepGoal(), updateTime - PROTECTED_StartTime);
            updateDataSet(goal, getContext().getString(R.string.steps_goal_dataSet));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState) {
        outState.putParcelableArrayList(STEPS_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.steps_dataSet)));
        outState.putParcelableArrayList(STEPS_CHART_GOAL_ENTRIES, getChartEntries(getContext().getString(R.string.steps_goal_dataSet)));
        outState.putStringArrayList(STEPS_CHART_LABELS, getLabels());
    }
}
