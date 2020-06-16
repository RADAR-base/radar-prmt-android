package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;

import com.garmin.health.customlog.LoggingResult;
import com.garmin.health.database.dtos.ZeroCrossingLog;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.sync.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (c) 2017 Garmin International. All Rights Reserved.
 * <p></p>
 * This software is the confidential and proprietary information of
 * Garmin International.
 * You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement
 * you entered into with Garmin International.
 * <p></p>
 * Garmin International MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. Garmin International SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * <p></p>
 * Created by jacksoncol on 10/25/18.
 */
public class ZeroCrossingLoggingChart extends GHLineChart
{
    private static final String ZC_CHART_ENTRIES = "ZC_CHART_ENTRIES";
    private static final String ZC_CHART_LABELS = "ZC_CHART_LABELS";
    private static final String ENERGY_CHART_ENTRIES = "ENERGY_CHART_ENTRIES";

    public ZeroCrossingLoggingChart(Context context)
    {
        super(context);
    }

    public ZeroCrossingLoggingChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ZeroCrossingLoggingChart(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    public void createChart(Bundle savedInstanceState, long appStartTime)
    {
        long startTime = appStartTime;

        if(savedInstanceState != null)
        {
            List<Entry> entries;
            List<Entry> energyEntries;
            List<String> labels;

            if(startTime < 0)
            {
                LoggingResult result = savedInstanceState.getParcelable(LoggingFragment.LOGGING_RESULT);

                if(result != null && !result.getZeroCrossingList().isEmpty())
                {
                    startTime = result.getZeroCrossingList().get(0).getTimestamp() * 1000;

                    LineDataSet dataSet = createDataSet(null, getContext().getString(R.string.zc_logging_dataset), Color.CYAN);
                    LineDataSet energyDataSet = createDataSet(null, getContext().getString(R.string.energy_logging_dataset), Color.MAGENTA);

                    List<LineDataSet> lineDataSets = new ArrayList<>();

                    lineDataSets.add(dataSet);
                    lineDataSets.add(energyDataSet);

                    createGHChart(null, lineDataSets, startTime);

                    for(ZeroCrossingLog log : result.getZeroCrossingList())
                    {
                        updateChart(new ChartData(log), log.getTimestamp() * 1000);
                    }
                }
                else
                {
                    setVisibility(GONE);
                }
            }
            else
            {
                entries = savedInstanceState.getParcelableArrayList(ZC_CHART_ENTRIES);
                energyEntries = savedInstanceState.getParcelableArrayList(ENERGY_CHART_ENTRIES);
                labels = savedInstanceState.getStringArrayList(ZC_CHART_LABELS);

                LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.zc_logging_dataset), Color.CYAN);
                LineDataSet energyDataSet = createDataSet(energyEntries, getContext().getString(R.string.energy_logging_dataset), Color.MAGENTA);

                List<LineDataSet> lineDataSets = new ArrayList<>();

                lineDataSets.add(dataSet);
                lineDataSets.add(energyDataSet);

                createGHChart(labels, lineDataSets, startTime);
            }
        }
    }

    @Override
    public void updateChart(ChartData result, long updateTime)
    {
        if(result != null && result.getLog() != null)
        {
            updateDataSet(new ChartData(result.getLog().getZeroCrossingCount(), updateTime), getContext().getString(R.string.zc_logging_dataset));
            updateDataSet(new ChartData(Long.valueOf(result.getLog().getEnergyTotal()).intValue(), updateTime), getContext().getString(R.string.energy_logging_dataset));
            updateGHChart(new ChartData(result.getLog().getZeroCrossingCount(), updateTime));
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(ZC_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.zc_logging_dataset)));
        outState.putParcelableArrayList(ENERGY_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.energy_logging_dataset)));
        outState.putStringArrayList(ZC_CHART_LABELS, getLabels());
    }
}
