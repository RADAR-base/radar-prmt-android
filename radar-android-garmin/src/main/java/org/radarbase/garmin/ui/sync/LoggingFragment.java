package org.radarbase.garmin.ui.sync;

import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.garmin.health.customlog.LoggingResult;
import com.garmin.health.database.dtos.HeartRateLog;
import com.garmin.health.database.dtos.HeartRateVariabilityLog;
import com.garmin.health.database.dtos.PulseOxLog;
import com.garmin.health.database.dtos.RespirationLog;
import com.garmin.health.database.dtos.StepLog;
import com.garmin.health.database.dtos.StressLog;
import com.garmin.health.database.dtos.ZeroCrossingLog;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.BaseFragment;
import org.radarbase.garmin.ui.charts.DayPieChart;
import org.radarbase.garmin.ui.charts.HeartRateLoggingChart;
import org.radarbase.garmin.ui.charts.HeartRateVariabilityLoggingChart;
import org.radarbase.garmin.ui.charts.PulseOxLoggingChart;
import org.radarbase.garmin.ui.charts.RespirationLoggingChart;
import org.radarbase.garmin.ui.charts.StepLoggingChart;
import org.radarbase.garmin.ui.charts.StressLoggingChart;
import org.radarbase.garmin.ui.charts.ZeroCrossingLoggingChart;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
 * Created by jacksoncol on 10/24/18.
 */
public class LoggingFragment extends BaseFragment
{
    private final static String TITLE = "title";
    public final static String LOGGING_RESULT = "logging.result";

    private static final int CHART_HEIGHT = 375;

    private int originalChartHeight = CHART_HEIGHT;

    private HeartRateLoggingChart mHrChart;
    private HeartRateVariabilityLoggingChart mHrvChart;
    private ZeroCrossingLoggingChart mZcChart;
    private StepLoggingChart mStepChart;
    private PulseOxLoggingChart mPulseOxChart;
    private StressLoggingChart mStressChart;
    private RespirationLoggingChart mRespirationChart;

    private DayPieChart mHrDay;
    private DayPieChart mHrvDay;
    private DayPieChart mZcDay;
    private TableLayout mStepDay;
    private DayPieChart mPoDay;
    private DayPieChart mStressDay;
    private DayPieChart mRespirationDay;

    public LoggingFragment() {}

    public static LoggingFragment getInstance(String title, LoggingResult loggingResult)
    {
        LoggingFragment loggingFragment = new LoggingFragment();

        Bundle bundle = new Bundle();
        bundle.putString(TITLE, title);
        bundle.putParcelable(LOGGING_RESULT, loggingResult);
        loggingFragment.setArguments(bundle);

        return loggingFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if(getArguments() != null)
        {
            setTitleInActionBar(getArguments().getString(TITLE));
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        if(getArguments() == null || getArguments().getParcelable(LOGGING_RESULT) == null)
        {
            return;
        }

        LoggingResult result = getArguments().getParcelable(LOGGING_RESULT);

        if(result == null)
        {
            return;
        }

        mHrChart = view.findViewById(R.id.hr_logging_chart);
        mHrvChart = view.findViewById(R.id.hrv_logging_chart);
        mZcChart = view.findViewById(R.id.zero_crossing_logging_chart);
        mStepChart = view.findViewById(R.id.step_logging_chart);
        mPulseOxChart = view.findViewById(R.id.pulse_ox_logging_chart);
        mStressChart = view.findViewById(R.id.stress_logging_chart);
        mRespirationChart = view.findViewById(R.id.respiration_logging_chart);

        mHrChart.createChart(getArguments());
        mHrvChart.createChart(getArguments());
        mZcChart.createChart(getArguments());
        mStepChart.createChart(getArguments());
        mPulseOxChart.createChart(getArguments());
        mStressChart.createChart(getArguments());
        mRespirationChart.createChart(getArguments());

        mHrDay = view.findViewById(R.id.hr_day_logging_chart);
        mHrvDay = view.findViewById(R.id.hrv_day_logging_chart);
        mZcDay = view.findViewById(R.id.zc_day_logging_chart);
        mStepDay = view.findViewById(R.id.step_table);
        mPoDay = view.findViewById(R.id.pulse_ox_day_logging_chart);
        mStressDay = view.findViewById(R.id.stress_day_logging_chart);
        mRespirationDay = view.findViewById(R.id.respiration_day_logging_chart);

        List<Long> hrTimestamp = new ArrayList<>();
        List<Long> hrvTimestamp = new ArrayList<>();
        List<Long> zcTimestamp = new ArrayList<>();
        List<Long> pulseOxTimestamp = new ArrayList<>();
        List<Long> stressTimestamp = new ArrayList<>();
        List<Long> respirationTimestamp = new ArrayList<>();

        for(HeartRateLog log : result.getHeartRateList())
        {
            hrTimestamp.add(log.getTimestamp());
        }

        for(HeartRateVariabilityLog log : result.getHrvList())
        {
            hrvTimestamp.add(log.getTimestampMs());
        }

        for(ZeroCrossingLog log : result.getZeroCrossingList())
        {
            zcTimestamp.add(log.getTimestamp());
        }

        for(PulseOxLog log : result.getPulseOxList())
        {
            pulseOxTimestamp.add(log.getTimestamp());
        }

        for(StressLog log : result.getStressList())
        {
            stressTimestamp.add(log.getTimestamp());
        }

        for(RespirationLog log : result.getRespirationList())
        {
            respirationTimestamp.add(log.getTimestamp());
        }

        mHrChart.setOnChartGestureListener(new LoggingChartGestureListener(mHrChart, mHrDay, hrTimestamp, 30));
        mHrvChart.setOnChartGestureListener(new LoggingChartGestureListener(mHrvChart, mHrvDay, hrvTimestamp, 10));
        mZcChart.setOnChartGestureListener(new LoggingChartGestureListener(mZcChart, mZcDay, zcTimestamp, 30));
        mStepChart.setOnChartGestureListener(new LoggingChartGestureListener(mStepChart, mStepDay, result, 60));
        mPulseOxChart.setOnChartGestureListener(new LoggingChartGestureListener(mPulseOxChart, mPoDay, pulseOxTimestamp, 30));
        mStressChart.setOnChartGestureListener(new LoggingChartGestureListener(mStressChart, mStressDay, stressTimestamp, 10));
        mRespirationChart.setOnChartGestureListener(new LoggingChartGestureListener(mRespirationChart, mRespirationDay, respirationTimestamp, 10));
    }

    @Override
    protected int getLayoutId()
    {
        return R.layout.fragment_logging;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        // saving chart state to bundle. This will help us to recreate the chart if app is killed or need to be recreated
        super.onSaveInstanceState(outState);

        mHrChart.saveChartState(outState);
        mZcChart.saveChartState(outState);
        mHrvChart.saveChartState(outState);
        mStepChart.saveChartState(outState);
        mPulseOxChart.saveChartState(outState);
        mStressChart.saveChartState(outState);
        mRespirationChart.saveChartState(outState);
    }

    /**
     * Resizes the chart
     * @param chart
     * @param width
     * @param height
     */
    private void resizeChart(LineChart chart, int width, int height)
    {
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) chart.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        chart.setLayoutParams(layoutParams);
    }

    /**
     * Listens to the chart gestures
     */
    public class LoggingChartGestureListener implements OnChartGestureListener
    {
        private final View dayChart;
        private LoggingResult loggingResult;
        private LineChart chart;

        private List<Long> timestampList;
        private int interval;
        private boolean isGenerated = false;

        private long lastHeldTimestamp;

        public LoggingChartGestureListener(LineChart chart1, DayPieChart dayPieChart, List<Long> timestampList, int interval)
        {
            chart = chart1;
            dayChart = dayPieChart;
            this.timestampList = timestampList;
            this.interval = interval;
            this.lastHeldTimestamp = System.currentTimeMillis();
        }

        public LoggingChartGestureListener(LineChart chart1, TableLayout dayPieChart, LoggingResult result, int interval)
        {
            chart = chart1;
            dayChart = dayPieChart;
            this.loggingResult = result;
            this.interval = interval;
            this.lastHeldTimestamp = System.currentTimeMillis();
        }

        @Override
        public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

        @Override
        public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

        @Override
        public void onChartLongPressed(MotionEvent me)
        {
            long timeNow = System.currentTimeMillis();

            if(timeNow - lastHeldTimestamp > 1000)
            {
                lastHeldTimestamp = timeNow;
                return;
            }

            // finds the display size
            Display display = getActivity().getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            //Check the chart's size and then resize
            int maxHeight = 3 * size.y / 4;

            if(chart.getHeight() == maxHeight)
            {
                //already full screen, so reduce the size
                LoggingFragment.this.resizeChart(chart, chart.getWidth(), originalChartHeight);
            }
            else
            {
                // make it full screen and allow pinch zoom
                originalChartHeight = chart.getHeight();
                LoggingFragment.this.resizeChart(chart, chart.getWidth(), maxHeight);
            }
        }

        @Override
        public void onChartDoubleTapped(MotionEvent me)
        {
            if(dayChart instanceof DayPieChart)
            {
                if(!((DayPieChart)dayChart).isCreated())
                {
                    ((DayPieChart)dayChart).createChart(this.timestampList, this.interval);
                }
            }
            else if(dayChart instanceof TableLayout && !isGenerated)
            {
                TableLayout table = (TableLayout)dayChart;
                renderStepData(loggingResult, table);
            }

            isGenerated = true;

            dayChart.setVisibility(dayChart.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onChartSingleTapped(MotionEvent me) { }

        @Override
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) { }

        @Override
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) { }

        @Override
        public void onChartTranslate(MotionEvent me, float dX, float dY) { }
    }

    private void renderStepData(LoggingResult result, TableLayout stepTable)
    {
        StepLog lastData = null;

        for(StepLog data : new ArrayList<StepLog>())
        {
            if(lastData != null && lastData.getEndTimestamp() != data.getStartTimestamp())
            {
                if(Math.abs(lastData.getEndTimestamp() - data.getStartTimestamp()) < 60)
                {
                    stepTable.addView(createSeparatorView(Color.LTGRAY, 2));
                }
                else
                {
                    stepTable.addView(createSeparatorView(Color.RED, 2));
                }
            }

            lastData = data;

            TableRow row = new TableRow(getContext());
            row.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            //time text view
            row.addView(createTextView(formatTimestamp(data.getEndTimestamp() * 1000, "MM/dd/yyyy HH:mm:ss")));

            // create text views with X, Y and Z axis data
            row.addView(createTextView(String.valueOf(data.getStepCount())));
            row.addView(createTextView(String.valueOf(data.getTotalSteps())));

            stepTable.addView(row);
        }

        stepTable.addView(createSeparatorView(Color.BLACK, 2));

    }

    private TableRow createSeparatorView(int color, int rows)
    {
        TableRow row = new TableRow(getContext());

        for (int i = 0; i < rows; i++)
        {
            row.addView(createColoredTextView(color));
        }

        row.setBackgroundColor(color);

        return row;
    }

    private View createColoredTextView(int color)
    {
        TextView view = new TextView(getContext());
        view.setBackgroundColor(color);
        view.setText(String.valueOf(""));
        addPadding(view);
        return view;
    }

    /**
     * Creates a text view for the given text value
     *
     * @param data
     * @return
     */
    @NonNull
    private TextView createTextView(String data)
    {
        TextView view = new TextView(getContext());
        view.setText(String.valueOf(data));
        addPadding(view);
        return view;
    }

    private void addPadding(TextView view)
    {
        view.setPadding(5, 5, 5, 5);
        view.setPadding(4, 4, 4, 4);
    }

    private String formatTimestamp(long in, String format)
    {
        String formattedDate = "";

        try
        {
            DateFormat dateFormat = new SimpleDateFormat(format + "  ");
            dateFormat.setTimeZone(TimeZone.getDefault());
            formattedDate = dateFormat.format(new Date(in));
        }
        catch (Exception e)
        {
            //in case of exception, send the same long timestamp as a string
            formattedDate = String.valueOf(in);
        }

        return formattedDate;
    }
}
