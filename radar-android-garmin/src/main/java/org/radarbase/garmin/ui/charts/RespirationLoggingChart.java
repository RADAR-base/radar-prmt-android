package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import com.garmin.health.customlog.LoggingResult;
import com.garmin.health.database.dtos.RespirationLog;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.sync.LoggingFragment;

import java.util.Collections;
import java.util.List;

/**
 * Copyright (c) 2017 Garmin International. All Rights Reserved.
 * <p/>
 * This software is the confidential and proprietary information of
 * Garmin International.
 * You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement
 * you entered into with Garmin International.
 * <p/>
 * Garmin International MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. Garmin International SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * <p/>
 * Created by jacksoncol on 2019-09-24.
 */
public class RespirationLoggingChart extends GHLineChart
{
    private static final String RESPIRATION_CHART_ENTRIES = "RESPIRATION_CHART_ENTRIES";
    private static final String RESPIRATION_CHART_LABELS = "RESPIRATION_CHART_LABELS";

    public RespirationLoggingChart(Context context)
    {
        super(context);
    }

    public RespirationLoggingChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public RespirationLoggingChart(Context context, AttributeSet attrs, int defStyle)
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

                if(result != null && !result.getRespirationList().isEmpty())
                {
                    startTime = result.getRespirationList().get(0).getTimestamp() * 1000;

                    LineDataSet dataSet = createDataSet(null, getContext().getString(R.string.respiration_logging_dataset), ColorTemplate.getHoloBlue());
                    createGHChart(null, Collections.singletonList(dataSet), startTime);

                    for(RespirationLog log : result.getRespirationList())
                    {
                        updateChart(new ChartData(Float.valueOf(log.getRespirationValue()).intValue(), log.getTimestamp() * 1000), log.getTimestamp() * 1000);
                    }
                }
                else
                {
                    setVisibility(GONE);
                }
            }
            else
            {
                entries = savedInstanceState.getParcelableArrayList(RESPIRATION_CHART_ENTRIES);
                labels = savedInstanceState.getStringArrayList(RESPIRATION_CHART_LABELS);

                LineDataSet dataSet = createDataSet(entries, getContext().getString(R.string.respiration_logging_dataset), ColorTemplate.getHoloBlue());
                createGHChart(labels, Collections.singletonList(dataSet), startTime);
            }
        }
    }

    @Override
    public void updateChart(ChartData result, long updateTime)
    {
        if(result != null)
        {
            updateDataSet(result, getContext().getString(R.string.respiration_logging_dataset));
            updateGHChart(result);
        }
    }

    @Override
    public void saveChartState(Bundle outState)
    {
        outState.putParcelableArrayList(RESPIRATION_CHART_ENTRIES, getChartEntries(getContext().getString(R.string.respiration_logging_dataset)));
        outState.putStringArrayList(RESPIRATION_CHART_LABELS, getLabels());
    }
}