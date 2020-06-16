package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;

import com.garmin.health.customlog.LoggingResult;
import com.garmin.health.database.dtos.HeartRateVariabilityLog;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
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
 * Created by jacksoncol on 10/25/18.
 */
public class HeartRateVariabilityLoggingChart extends GHLineChart
{
    private static final String HRV_CHART_ENTRIES = "HRV_CHART_ENTRIES";
    private static final String HRV_CHART_LABELS = "HRV_CHART_LABELS";

    public HeartRateVariabilityLoggingChart(Context context)
    {
        super(context);
    }

    public HeartRateVariabilityLoggingChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public HeartRateVariabilityLoggingChart(Context context, AttributeSet attrs, int defStyle)
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

                if(result != null && !result.getHrvList().isEmpty())
                {
                    startTime = result.getHrvList().get(0).getTimestampMs();

                    LineDataSet dataSet = createDataSet(null, getContext().getString(R.string.hrv_logging_dataset), Color.GREEN);
                    createGHChart(null, Collections.singletonList(dataSet), startTime);

                    for(HeartRateVariabilityLog log : result.getHrvList())
                    {
                        updateChart(new ChartData(log.getBeatBeatInterval(), log.getTimestampMs()), log.getTimestampMs());
                    }
                }
                else
                {
                    setVisibility(GONE);
                }
            }
            else
            {
                entries = savedInstanceState.getParcelableArrayList(HRV_CHART_ENTRIES);
                labels = savedInstanceState.getStringArrayList(HRV_CHART_LABELS);

                LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.hrv_logging_dataset), Color.GREEN);
                createGHChart(labels, Collections.singletonList(dataSet), startTime);
            }
        }
    }

    @Override
    public void updateChart(ChartData result, long updateTime)
    {
        if(result != null)
        {
            updateDataSet(result, getContext().getString(R.string.hrv_logging_dataset));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(HRV_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.hrv_logging_dataset)));
        outState.putStringArrayList(HRV_CHART_LABELS, getLabels());
    }
}
