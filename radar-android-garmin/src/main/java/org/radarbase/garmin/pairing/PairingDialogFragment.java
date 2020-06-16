package org.radarbase.garmin.pairing;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.garmin.health.AuthCompletion;
import com.garmin.health.AuthRetryCompletion;
import com.garmin.health.Device;
import com.garmin.health.DeviceManager;
import com.garmin.health.PairingCallback;
import com.garmin.health.ScannedDevice;
import com.garmin.health.bluetooth.PairingFailedException;

import org.radarbase.garmin.R;

import java.util.concurrent.Future;

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
public class PairingDialogFragment extends DialogFragment {
    public static final String DEVICE_ARG = "args";

    private ScannedDevice mDevice;
    private boolean mHasReset = false;

    private Future mPairingFuture;
    private ProgressBar mPairingProgressBar;
    private View mRootView;
    private DevicePairingCallback mCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        this.setRetainInstance(true);

        if (mRootView != null) {
            return mRootView;
        }

        mRootView = inflater.inflate(R.layout.fragment_pairing, container, false);

        mPairingProgressBar = mRootView.findViewById(R.id.pairing_progress_bar);
        Animator mPairingProgressBarAnimator = ObjectAnimator.ofInt(mPairingProgressBar, "progress", 0, 100);
        mPairingProgressBarAnimator.setDuration(2000);
        mPairingProgressBarAnimator.start();

        mDevice = getArguments().getParcelable(DEVICE_ARG);
        // pairs the device discovered during scanning

        mCallback = new DevicePairingCallback();

        mPairingFuture = DeviceManager.getDeviceManager().pair(mDevice, mCallback);
        setPairing(true);

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();

        if (actionBar != null) {
            actionBar.setTitle(R.string.pairing_title);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mPairingFuture != null && !mPairingFuture.isDone()) {
            mPairingFuture.cancel(true);
        }
    }

    private void setPairing(boolean isPairing) {
        mPairingProgressBar.setVisibility((isPairing) ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Callback for the handling the pairing events
     */
    private class DevicePairingCallback implements PairingCallback
    {
        @Override
        public void pairingSucceeded(Device device)
        {
            Toast.makeText(getContext(), String.format("Pairing %s was successful!", mDevice.friendlyName()), Toast.LENGTH_SHORT).show();

            setPairing(false);

            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();

            fragmentManager.popBackStack();
            fragmentManager.popBackStack();
        }

        @Override
        public void pairingFailed(PairingFailedException e)
        {
            if(mPairingFuture == null || mPairingFuture.isCancelled()) {
                return;
            }

            Toast.makeText(getContext(), "Pairing Failed, Please try again.", Toast.LENGTH_SHORT).show();

            setPairing(false);
        }

        @Override
        public void authRequested(final AuthCompletion authCompletion)
        {
            if(mPairingFuture == null || mPairingFuture.isCancelled())
            {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.enter_passkey);

            final EditText input = new EditText(getContext());

            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);

            builder.setPositiveButton(R.string.button_ok, (dialog, which) -> {
                String text = input.getText().toString();
                int passkey = Integer.parseInt(text);
                authCompletion.setPasskey(passkey);
            });

            builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());

            builder.show();
        }

        @Override
        public void authFailed(AuthRetryCompletion completion)
        {
            completion.shouldRetry(true);
        }

        @Override
        public void authTimeout()
        {
            Toast.makeText(getContext(), "Pairing Failed, Please try again.", Toast.LENGTH_SHORT).show();

            setPairing(false);
        }
    }
}
