package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import com.garmin.health.customlog.LoggingResult;
import com.garmin.health.database.dtos.StressLog;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.sync.LoggingFragment;

import java.util.Collections;
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
 * Created by jacksoncol on 2/8/19.
 */
public class StressLoggingChart extends GHLineChart
{
    private static final String STRESS_CHART_ENTRIES = "STRESS_CHART_ENTRIES";
    private static final String STRESS_CHART_LABELS = "STRESS_CHART_LABELS";

    public StressLoggingChart(Context context)
    {
        super(context);
    }

    public StressLoggingChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public StressLoggingChart(Context context, AttributeSet attrs, int defStyle)
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
            List<String> labels;

            if(startTime < 0)
            {
                LoggingResult result = savedInstanceState.getParcelable(LoggingFragment.LOGGING_RESULT);

                if(result != null && !result.getStressList().isEmpty())
                {
                    startTime = result.getStressList().get(0).getTimestamp() * 1000;

                    LineDataSet dataSet = createDataSet(null, getContext().getString(R.string.stress_logging_dataset), ColorTemplate.getHoloBlue());
                    createGHChart(null, Collections.singletonList(dataSet), startTime);

                    for(StressLog log : result.getStressList())
                    {
                        updateChart(new ChartData(log.getStressScore(), log.getTimestamp() * 1000), log.getTimestamp() * 1000);
                    }
                }
                else
                {
                    setVisibility(GONE);
                }
            }
            else
            {
                entries = savedInstanceState.getParcelableArrayList(STRESS_CHART_ENTRIES);
                labels = savedInstanceState.getStringArrayList(STRESS_CHART_LABELS);

                LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.stress_logging_dataset), ColorTemplate.getHoloBlue());
                createGHChart(labels, Collections.singletonList(dataSet), startTime);
            }
        }
    }

    @Override
    public void updateChart(ChartData result, long updateTime)
    {
        if(result != null)
        {
            updateDataSet(result, getContext().getString(R.string.stress_logging_dataset));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(STRESS_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.stress_logging_dataset)));
        outState.putStringArrayList(STRESS_CHART_LABELS, getLabels());
    }
}
