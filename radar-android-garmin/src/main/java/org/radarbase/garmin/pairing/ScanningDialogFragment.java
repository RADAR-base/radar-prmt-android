package org.radarbase.garmin.pairing;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.garmin.health.Device;
import com.garmin.health.DeviceManager;
import com.garmin.health.GarminDeviceScanner;
import com.garmin.health.ScannedDevice;
import com.google.android.material.snackbar.Snackbar;

import org.radarbase.garmin.R;
import org.radarbase.garmin.adapters.ScannedDeviceListAdapter;
import org.radarbase.garmin.ui.BaseFragment;

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
 * Created by jacksoncol on 6/22/17.
 */
public class ScanningDialogFragment extends BaseFragment
{
    private final static int REQUEST_ENABLE_BT = 1;
    private TextView mScanText;

    private ProgressBar mScanProgressBar;
    private ObjectAnimator mScanProgressBarAnimator;

    private ScannedDeviceListAdapter mListAdapter;

    private View mRootView;

    public ScanningDialogFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState)
    {
        mRootView = super.onCreateView(inflater, container, savedInstanceState);

        this.setRetainInstance(true);

        mScanText = mRootView.findViewById(R.id.scan_text);

        mScanProgressBar = mRootView.findViewById(R.id.scanning_icon);
        mScanProgressBarAnimator = ObjectAnimator.ofInt(mScanProgressBar, "progress", 0, 100);
        mScanProgressBarAnimator.setDuration(2000);

        ListView scannedDeviceList = mRootView.findViewById(R.id.scanned_device_list);
        scannedDeviceList.setMinimumHeight((int)(Resources.getSystem().getDisplayMetrics().heightPixels * .8));

        mListAdapter = new ScannedDeviceListAdapter(getActivity().getApplicationContext());

        scannedDeviceList.setAdapter(mListAdapter);

        scannedDeviceList.setOnItemClickListener((adapterView, view, position, id) ->
        {
            ScannedDevice chosenScannedDevice = (ScannedDevice) adapterView.getItemAtPosition(position);

            pair(chosenScannedDevice);
        });

        // listens to the switch action
        Switch mScanSwitch = mRootView.findViewById(R.id.scan_switch);

        mScanSwitch.setOnCheckedChangeListener((compoundButton, checked) ->
        {
            if(checked)
            {
                startScan();
            }
            else
            {
                stopScan();
            }
        });

        return mRootView;
    }

    @Override
    public void onStop()
    {
        super.onStop();

        stopScan();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();

        if(actionBar != null)
        {
            actionBar.setTitle(R.string.scanning_title);
        }

        startScan();
    }

    @Override
    protected int getLayoutId()
    {
        return R.layout.fragment_scanning;
    }

    private void startScan()
    {
        mListAdapter.open();

        mScanProgressBar.setVisibility(View.VISIBLE);
        mScanProgressBarAnimator.start();
        mScanText.setText(getString(R.string.scan_on));

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // if Bluetooth is not enabled, request to enable Bluetooth
        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
        else
        {
            performScan();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // if bluetooth is enabled
        if(requestCode == REQUEST_ENABLE_BT)
        {
            if(resultCode == Activity.RESULT_OK)
            {
                performScan();
            }
            else
            {
                Log.e("ScanningDialog", "Bluetooth has not been enabled. Application cannot proceed.");
                Snackbar.make(mRootView, "Bluetooth not enabled.", Snackbar.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Performs scan by assuming bluetooth has been enabled
     */
    private void performScan() {
        callback.startScan(getContext());
    }

    private void stopScan()
    {
        mScanProgressBar.setVisibility(View.INVISIBLE);
        mScanText.setText(getString(R.string.scan_off));

        if (callback != null) {
            callback.stopScan();
        }

        mListAdapter.close();
    }

    public void pair(ScannedDevice scannedDevice)
    {
        PairingDialogFragment pairingFragment = new PairingDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(PairingDialogFragment.DEVICE_ARG, scannedDevice);
        pairingFragment.setArguments(args);

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

        transaction.replace(R.id.main_root, pairingFragment, pairingFragment.getTag()).addToBackStack(getTag()).commit();
    }

    private GarminDeviceScanner callback = new GarminDeviceScanner()
    {
        @Override
        public void onBatchScannedDevices(List<ScannedDevice> devices) {}

        @Override
        public void onScannedDevice(ScannedDevice device)
        {
            for(Device mDevice : DeviceManager.getDeviceManager().getPairedDevices())
            {
                if(mDevice.address().equalsIgnoreCase(device.address()))
                {
                    return;
                }
            }

            getActivity().runOnUiThread(() ->
                    mListAdapter.addDevice(device));

        }

        @Override
        public void onScanFailed(Integer errorCode)
        {
            getActivity().runOnUiThread(() ->
                Toast.makeText(getActivity().getApplicationContext(), "Scanning failed", Toast.LENGTH_SHORT).show());
        }
    };
}
