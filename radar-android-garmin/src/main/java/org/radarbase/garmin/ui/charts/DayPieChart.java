package org.radarbase.garmin.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

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
 * Created by jacksoncol on 11/16/18.
 */
public class DayPieChart extends PieChart
{
    private boolean mCreated;

    List<Long> mValues = new ArrayList<>();

    private long mStart;
    private long mEnd;

    private long mNow;

    public DayPieChart(Context context)
    {
        super(context);

        setOnChartGestureListener(mOnChartGestureListener);
    }

    public DayPieChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public DayPieChart(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public boolean isCreated() {
        return mCreated;
    }

    public void createChart(List<Long> data, int interval)
    {
        mValues.addAll(data);

        long offset = TimeZone.getDefault().getOffset(mValues.get(0));

        for(int i = 0; i < mValues.size(); i++)
        {
            mValues.set(i, (mValues.get(i) > (System.currentTimeMillis() / 1000) ? (mValues.get(i) + offset) / 1000 : (mValues.get(i) + (offset / 1000))) % 86400);
        }

        mNow = (System.currentTimeMillis() + offset) / 1000 % 86400;

        Collections.sort(mValues);

        long startTimestamp = mValues.get(0);
        long endTimestamp = mValues.get(mValues.size() - 1);

        mStart = startTimestamp;
        mEnd = endTimestamp;

        setUsePercentValues(false);
        setHighlightPerTapEnabled(false);
        setDescription(null);
        setRotationEnabled(false);
        setDrawSliceText(false);
        getLegend().setEnabled(false);
        setDrawMarkerViews(false);

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)getLayoutParams();
        layoutParams.height *= 2;
        setLayoutParams(layoutParams);

        PieDataSet set = createDataSets(interval);

        String[] strings = new String[set.getEntryCount()];
        Arrays.fill(strings, "");
        setData(new PieData(strings, set));

        mCreated = true;
    }

    private PieDataSet createDataSets(int interval)
    {
        List<Entry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        long sum = 0;

        boolean on = true;
        boolean now = false;
        long last = mStart;
        int i = -1;

        if(mStart != 0)
        {
            sum = mStart;

            entries.add(new Entry(mStart, ++i));
            colors.add(Color.GRAY);
        }

        for(Long l : mValues)
        {
            if(!on)
            {
                long magenta = last - sum;

                on = true;
                entries.add(new Entry(magenta, ++i));
                colors.add(Color.MAGENTA);

                sum += magenta;
            }

            if(l - last > interval)
            {
                long cyan = last - sum;

                entries.add(new Entry(cyan, ++i));
                colors.add(Color.CYAN);
                on = false;

                sum += cyan;
            }

            last = l;

            if(last > mNow && !now)
            {
                entries.add(new Entry(900, ++i));
                colors.add(Color.BLACK);
                now = true;
            }
        }

        if(mEnd != 86400)
        {
            entries.add(new Entry(mValues.get(mValues.size() - 1) - sum, ++i));
            colors.add(on ? Color.CYAN : Color.MAGENTA);

            if(!now)
            {
                entries.add(new Entry(900, ++i));
                colors.add(Color.BLACK);
            }

            entries.add(new Entry(86400 - mEnd, ++i));
            colors.add(Color.GRAY);
        }

        PieDataSet set = new PieDataSet(entries, "DATA");
        set.setColors(colors);
        set.setDrawValues(false);

        return set;
    }

    OnChartGestureListener mOnChartGestureListener = new OnChartGestureListener()
    {
        @Override
        public void onChartGestureStart(MotionEvent me, ChartGesture lastPerformedGesture) {

        }

        @Override
        public void onChartGestureEnd(MotionEvent me, ChartGesture lastPerformedGesture)
        {

        }

        @Override
        public void onChartLongPressed(MotionEvent me) {

        }

        @Override
        public void onChartDoubleTapped(MotionEvent me)
        {
            setVisibility(GONE);
        }

        @Override
        public void onChartSingleTapped(MotionEvent me) {

        }

        @Override
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

        }

        @Override
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

        }

        @Override
        public void onChartTranslate(MotionEvent me, float dX, float dY) {

        }
    };
}
