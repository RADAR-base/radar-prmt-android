package org.radarbase.garmin.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.garmin.device.realtime.RealTimeDataType;
import com.garmin.device.realtime.RealTimeResult;
import com.garmin.device.realtime.listeners.RealTimeDataListener;
import com.garmin.health.DeviceManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.android.material.snackbar.Snackbar;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.radarbase.garmin.R;
import org.radarbase.garmin.devices.PairedDevicesDialogFragment;
import org.radarbase.garmin.ui.charts.AccelerometerChart;
import org.radarbase.garmin.ui.charts.CaloriesChart;
import org.radarbase.garmin.ui.charts.FloorsChart;
import org.radarbase.garmin.ui.charts.GHLineChart;
import org.radarbase.garmin.ui.charts.HeartRateChart;
import org.radarbase.garmin.ui.charts.HeartRateVariabilityChart;
import org.radarbase.garmin.ui.charts.IntensityMinutesChart;
import org.radarbase.garmin.ui.charts.RealTimeChartData;
import org.radarbase.garmin.ui.charts.RespirationChart;
import org.radarbase.garmin.ui.charts.Spo2Chart;
import org.radarbase.garmin.ui.charts.StepsChart;
import org.radarbase.garmin.ui.charts.StressLevelChart;
import org.radarbase.garmin.ui.realtime.RealTimeDataHandler;

public class DataDisplayActivity extends BaseGarminHealthActivity implements RealTimeDataListener {
    public static final String APP_START_TIME = "appStartTime";
    private static final int CHART_HEIGHT = 375;

    private static final String TAG = DataDisplayActivity.class.getSimpleName();

    private String mAddress;
    private int originalChartHeight = CHART_HEIGHT;

    private HeartRateChart hrChart;
    private HeartRateVariabilityChart hrvChart;
    private StepsChart stepsChart;
    private CaloriesChart caloriesChart;
    private FloorsChart floorsChart;
    private IntensityMinutesChart intensityMinutesChart;
    private StressLevelChart stressChart;
    private AccelerometerChart accelerometerChart;
    private Spo2Chart spo2Chart;
    private RespirationChart respirationChart;

    private long startTime;
    private List<GHLineChart> charts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time);
        setTitle(R.string.title_real_time);
        setChartToolbar();

        Intent intent = getIntent();
        mAddress = intent.getStringExtra(PairedDevicesDialogFragment.DEVICE_ADDRESS_EXTRA);
        DeviceManager deviceManager = DeviceManager.getDeviceManager();

        initRealTimeData();

        // Initialize charts
        Point size = findDisplaySize();

        hrChart = findViewById(R.id.hr_chart);
        hrvChart = findViewById(R.id.hrv_chart);
        stepsChart = findViewById(R.id.steps_chart);
        caloriesChart = findViewById(R.id.calories_chart);
        floorsChart = findViewById(R.id.floor_chart);
        intensityMinutesChart = findViewById(R.id.intensity_minutes_chart);
        stressChart = findViewById(R.id.stress_chart);
        accelerometerChart = findViewById(R.id.accelerometer_chart);
        spo2Chart = findViewById(R.id.spo2_chart);
        respirationChart = findViewById(R.id.respiration_chart);

        if(savedInstanceState != null)
        {
            startTime = savedInstanceState.getLong(APP_START_TIME);
        }
        startTime = (startTime == 0) ? System.currentTimeMillis() : startTime;
        charts = Arrays.asList(hrChart, hrvChart, stressChart, stepsChart, caloriesChart, intensityMinutesChart, floorsChart, accelerometerChart, spo2Chart, respirationChart);

        // create GH charts
        for(GHLineChart chart : charts)
        {
            if(chart != null )
            {
                chart.createChart(savedInstanceState, startTime);
                // resizeChart(chart, size.x, CHART_HEIGHT);
                // apply the gesture listener
                chart.setOnChartGestureListener(new HealthChartGestureListener(chart));
            }
        }

        // show the message
        showSnackbarMessage(R.string.all_charts_refreshed);
    }

    /**
     * Sets char toolbar
     */
    private void setChartToolbar() {
        // Toolbar
        Toolbar chartToolbar = findViewById(R.id.chart_toolbar);
        chartToolbar.setTitle(getString(R.string.garmin_health_charts));
        setSupportActionBar(chartToolbar);

//        ActionBar actionBar = getSupportActionBar();
//        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // inflates the chart menu
       // getMenuInflater().inflate(R.menu.menu_charts, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
//            case R.id.chart_refresh:
//                refreshCharts();
//                return true;
//
//            case R.id.show_hr_chart:
//                toggleChartCard(item, R.id.hr_chart_card);
//                return true;
//
//            case R.id.show_hrv_chart:
//                toggleChartCard(item, R.id.hrv_chart_card);
//                return true;
//
//            case R.id.show_stress_chart:
//                toggleChartCard(item, R.id.stress_chart_card);
//                return true;
//
//            case R.id.show_steps_chart:
//                toggleChartCard(item, R.id.steps_chart_card);
//                return true;
//
//            case R.id.show_calories_chart:
//                toggleChartCard(item, R.id.calories_chart_card);
//                return true;
//
//            case R.id.show_intensity_minutes_chart:
//                toggleChartCard(item, R.id.intensity_minutes_chart_card);
//                return true;
//
//            case R.id.show_floors_chart:
//                toggleChartCard(item, R.id.floor_chart_card);
//                return true;
//
//            case R.id.accelerometer_chart:
//                toggleChartCard(item, R.id.accelerometer_chart_card);
//                return true;
//
//            case R.id.spo2_chart:
//                toggleChartCard(item, R.id.spo2_chart_card);
//                return true;
//
//            case R.id.respiration_chart:
//                toggleChartCard(item, R.id.respiration_chart_card);
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Toggles chart card based on the menu selection
     * @param item
     * @param chartCardId
     */
    private void toggleChartCard(MenuItem item, int chartCardId ){
        boolean checked = item.isChecked();
        item.setChecked(checked? false : true);
        findViewById(chartCardId).setVisibility(checked ? View.GONE : View.VISIBLE);
    }

    private void initRealTimeData() {
        //Data may have been received when page wasn't in the foreground
        HashMap<RealTimeDataType, RealTimeResult> latestData = RealTimeDataHandler.getInstance().getLatestData(mAddress);
        if (latestData != null) {
            for (RealTimeDataType type : latestData.keySet()) {
                updateData(type, latestData.get(type));
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        DeviceManager.getDeviceManager().enableRealTimeData(mAddress, EnumSet.allOf(RealTimeDataType.class));
        DeviceManager.getDeviceManager().addRealTimeDataListener(this, EnumSet.allOf(RealTimeDataType.class));
    }

//    @Override
//    protected void onStop()
//    {
//        super.onStop();
//
//        //Activity isn't running, don't need to listen for data
//        DeviceManager.getDeviceManager().removeRealTimeDataListener(this, EnumSet.allOf(RealTimeDataType.class));
//        DeviceManager.getDeviceManager().disableRealTimeData(mAddress, EnumSet.allOf(RealTimeDataType.class));
//    }

    @Override
    @MainThread
    public void onDataUpdate(@NonNull String macAddress, @NonNull final RealTimeDataType dataType, @NonNull final RealTimeResult result)
    {
        if(!macAddress.equals(mAddress))
        {
            //Real time data came from different device
            return;
        }

        //Use same logging as single instance real time listener for sample
        RealTimeDataHandler.logRealTimeData(TAG, macAddress, dataType, result);

        updateData(dataType, result);
    }

    private void updateData(final RealTimeDataType dataType, final RealTimeResult result)
    {
        if (dataType == null || result == null)
        {
            return;
        }

        //Update views with new data
        switch (dataType)
        {
            case HEART_RATE:
                hrChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case HEART_RATE_VARIABILITY:
                hrvChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case STRESS:
                stressChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case STEPS:
                stepsChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case CALORIES:
                caloriesChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case ASCENT:
                floorsChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case INTENSITY_MINUTES:
                intensityMinutesChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case ACCELEROMETER:
                accelerometerChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case SPO2:
                spo2Chart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            case RESPIRATION:
                respirationChart.updateChart(new RealTimeChartData(result), System.currentTimeMillis());
                break;
            default:
                break;
        }
    }

    //Permission needed to save real time data to external file
    private boolean requestStoragePermission()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1)
        {
            return true;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        {
            return true;
        }

        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // saving chart state to bundle. This will help us to recreate the chart if app is killed or need to be recreated
        super.onSaveInstanceState(outState);
        for (GHLineChart chart : charts) {
            if (chart != null) {
                chart.saveChartState(outState);
            }
        }
        outState.putLong(APP_START_TIME, startTime);
    }


    /**
     * Shows the provided message as a snackbar text
     * @param resId
     */
    private  void showSnackbarMessage(@StringRes int resId){
        Snackbar refreshMessage = Snackbar.make(findViewById(R.id.hr_chart), resId, Snackbar.LENGTH_LONG);
        refreshMessage.show();
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
     * finds the display size of the device
     * @return
     */
    private Point findDisplaySize(){

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size;
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }
    /**
     * Listens to the chart gestures
     */
    public class HealthChartGestureListener implements OnChartGestureListener
    {
        private LineChart chart;

        public HealthChartGestureListener(LineChart chart1)
        {
            chart = chart1;
        }

        @Override
        public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

        @Override
        public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

        @Override
        public void onChartLongPressed(MotionEvent me) {}

        @Override
        public void onChartDoubleTapped(MotionEvent me)
        {
            // finds the display size
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            //Check the chart's size and then resize
            int maxHeight = 3*size.y/4;

            if(chart.getHeight() == maxHeight)
            {
                //already full screen, so reduce the size
                DataDisplayActivity.this.resizeChart(chart, chart.getWidth(), originalChartHeight);
            }
            else
            {
                // make it full screen and allow pinch zoom
                originalChartHeight = chart.getHeight();
                DataDisplayActivity.this.resizeChart(chart, chart.getWidth(), maxHeight);
            }
        }

        @Override
        public void onChartSingleTapped(MotionEvent me) {}

        @Override
        public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}

        @Override
        public void onChartScale(MotionEvent me, float scaleX, float scaleY) {}

        @Override
        public void onChartTranslate(MotionEvent me, float dX, float dY) {}


    }

}
