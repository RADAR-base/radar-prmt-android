package org.radarbase.garmin.devices;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.garmin.health.Device;
import com.garmin.health.DeviceManager;

import org.radarbase.garmin.R;
import org.radarbase.garmin.adapters.SettingsAdapter;

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
 * Created by jacksoncol on 7/5/17.
 */

public class DeviceSettingsDialogFragment extends DialogFragment
{
    private ImageView mDeviceImage;
    private View mRootView;
    private ListView mSettingsList;
    private Button mForgetButton;
    private SettingsAdapter mSettingsAdapter;

    private Device mDevice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        this.setRetainInstance(true);

        if(mDevice == null)
        {
            mDevice = DeviceManager.getDeviceManager().getDevice(getArguments().getString(PairedDevicesDialogFragment.DEVICE_ARG));
        }

        mRootView = inflater.inflate(R.layout.fragment_device_settings, container, false);

        mDeviceImage = mRootView.findViewById(R.id.device_image);

        mSettingsList = mRootView.findViewById(R.id.settings_list);
        mSettingsAdapter = new SettingsAdapter(mDevice, getContext());
        mSettingsList.setAdapter(mSettingsAdapter);

        mForgetButton = mRootView.findViewById(R.id.forget_button);

        mForgetButton.setOnClickListener(forgetListener);

        mDeviceImage.setImageResource(mDevice.image());

        return mRootView;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();

        if(actionBar != null)
        {
            actionBar.setTitle(R.string.device_settings);
        }
    }

    private View.OnClickListener forgetListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View view)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

            builder.setMessage(String.format("Forget Device: %s?", mDevice.friendlyName()));

            builder.setPositiveButton(R.string.y, (dialogInterface, i) -> {
                DeviceManager.getDeviceManager().forget(mDevice.address());

                getFragmentManager().popBackStack();
                getFragmentManager().popBackStack();
            });

            builder.setNegativeButton("No", (dialogInterface, i) -> {/* Do nothing...*/});

            builder.create().show();
        }
    };
}
