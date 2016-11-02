package org.radarcns.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import org.radarcns.R;
import org.radarcns.empaticaE4.E4DeviceStatus;
import org.radarcns.empaticaE4.E4ServiceConnection;
import org.radarcns.empaticaE4.MainActivity;

import java.text.DecimalFormat;
import java.util.Random;

import static org.radarcns.android.DeviceStatusListener.Status.*;

public class OverviewActivity extends MainActivity {
    private TextView[] mDeviceNameLabels;
    private View[] mStatusIcons;
    private TextView[] mTemperatureLabels;
    private TextView[] mBatteryLabels;

    private DeviceUIUpdater mUIUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);

        // Create arrays of labels. Fixed to four rows
        mDeviceNameLabels = new TextView[] {
                (TextView) findViewById(R.id.deviceNameRow1),
                (TextView) findViewById(R.id.deviceNameRow2),
                (TextView) findViewById(R.id.deviceNameRow3),
                (TextView) findViewById(R.id.deviceNameRow4)
        };

        mStatusIcons = new View[] {
                findViewById(R.id.statusRow1),
                findViewById(R.id.statusRow2),
                findViewById(R.id.statusRow3),
                findViewById(R.id.statusRow4)
        };

        mTemperatureLabels = new TextView[] {
                (TextView) findViewById(R.id.temperatureRow1),
                (TextView) findViewById(R.id.temperatureRow2),
                (TextView) findViewById(R.id.temperatureRow3),
                (TextView) findViewById(R.id.temperatureRow4)
        };

        mBatteryLabels = new TextView[] {
                (TextView) findViewById(R.id.batteryRow1),
                (TextView) findViewById(R.id.batteryRow2),
                (TextView) findViewById(R.id.batteryRow3),
                (TextView) findViewById(R.id.batteryRow4)
        };

        mConnections = new E4ServiceConnection[] {
                new E4ServiceConnection(this, 0), new E4ServiceConnection(this, 1), new E4ServiceConnection(this, 2), new E4ServiceConnection(this, 3)
        };

        uiRefreshRate = getResources().getInteger(R.integer.ui_refresh_rate);
        mHandler = new Handler();

        mUIUpdater = new DeviceUIUpdater();
        mUIScheduler = new Runnable() {
            @Override
            public void run() {
                try {
                    E4ServiceConnection connection = getActiveConnection();
                    if (connection != null) {
                        mUIUpdater.updateWithData(connection);
                    }
                } catch (RemoteException e) {
                    logger.warn("Failed to update device data", e);
                } finally {
                    mHandler.postDelayed(mUIScheduler, uiRefreshRate);
                }
            }
        };

        isForcedDisconnected = false;
        checkBluetoothPermissions();

    }

    public void connectDevice(View v) {
        int rowIndex = getRowIndexFromView(v);

        // some test code, updating with random data
        Random generator = new Random();
        updateDeviceName(String.valueOf( generator.hashCode() ), rowIndex);
    }

    public void showDetails(View v) {
        int rowIndex = getRowIndexFromView(v);

        // some test code, updating with random data
        E4DeviceStatus deviceData = new E4DeviceStatus();

        Random generator = new Random();
        deviceData.setTemperature(  generator.nextFloat()*100 );
        deviceData.setBatteryLevel( generator.nextFloat() );
        deviceData.setStatus( new DeviceStatusListener.Status[] {CONNECTED,DISCONNECTED,READY,CONNECTING}[generator.nextInt(4)] );

        updateRow(deviceData, rowIndex);
    }

    private int getRowIndexFromView(View v) {
        // Assume all elements are direct descendants from the TableRow
        View parent = (View) v.getParent();
        switch ( parent.getId() ) {

            case R.id.row1:
                return 0;

            case R.id.row2:
                return 1;

            case R.id.row3:
                return 2;

            case R.id.row4:
                return 3;

            default:
                return -1; // TODO: throw exception
        }
    }

    public void updateDeviceName(String deviceName, int row) {
        // TODO: restrict n_characters of deviceName
        mDeviceNameLabels[row].setText(deviceName);
    }

    /**
     * Updates a row with the deviceData
     * @param deviceData
     * @param row           Row number
     */
    public void updateRow(E4DeviceStatus deviceData, int row ) {
        final DecimalFormat singleDecimal = new DecimalFormat("0.0");
        final DecimalFormat doubleDecimal = new DecimalFormat("0.00");
        final DecimalFormat noDecimals = new DecimalFormat("0");

        // Connection status. Change icon used.
        switch (deviceData.getStatus()) {
            case CONNECTED:
                mStatusIcons[row].setBackgroundResource(R.drawable.status_connected);
                break;
            case DISCONNECTED:
                mStatusIcons[row].setBackgroundResource(R.drawable.status_disconnected);
                break;
            case READY:
            case CONNECTING:
                mStatusIcons[row].setBackgroundResource(R.drawable.status_searching);
                break;
            default:
                mStatusIcons[row].setBackgroundResource(R.drawable.status_searching);
        }

        // Temperature
        setText(mTemperatureLabels[row], deviceData.getTemperature(), "\u2103", singleDecimal);

        // Battery
        setText(mBatteryLabels[row], 100*deviceData.getBatteryLevel(), "%", noDecimals);

    }

    private void setText(TextView label, float value, String suffix, DecimalFormat formatter) {
        if (Float.isNaN(value)) {
            // em dash
            label.setText("\u2014");
        } else {
            label.setText(formatter.format(value) + " " + suffix);
        }
    }

    public class DeviceUIUpdater implements Runnable {
        E4DeviceStatus deviceData = null;

        public void updateWithData(@NonNull E4ServiceConnection connection) throws RemoteException {
            deviceData = connection.getDeviceData();
            runOnUiThread(this);
        }

        @Override
        public void run() {
            if (deviceData == null) {
                return;
            }
            updateRow(deviceData, 0);
        }


    }

    /** Called when the user clicks the Send button */
    /*
    public void sendMessage(View view) {
        Intent intent = new Intent(this, MainActivity.class);
//        EditText editText = (EditText) findViewById(R.id.edit_message);
//        String message = editText.getText().toString();
        intent.putExtra("An extra message", "Started from over");
        startActivity(intent);
    }
    */

}
