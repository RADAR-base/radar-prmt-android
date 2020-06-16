package org.radarbase.garmin.devices;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.garmin.health.ConnectionState;
import com.garmin.health.Device;
import com.garmin.health.DeviceConnectionStateListener;
import com.garmin.health.DeviceManager;
import com.garmin.health.SetupState;
import com.garmin.health.bluetooth.FailureCode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.radarbase.garmin.R;
import org.radarbase.garmin.adapters.PairedDeviceListAdapter;
import org.radarbase.garmin.pairing.ScanningDialogFragment;
import org.radarbase.garmin.ui.DataDisplayActivity;
import org.radarbase.garmin.ui.realtime.RealTimeDataHandler;
import org.radarbase.garmin.util.ConfirmationDialog;
import org.radarbase.garmin.utils.MapperUtil;

/**
 * Shows a list of paired Garmin devices. It also provides a way to perform bluetooth scanning and to add a new device.
 *
 * @author morajkar
 */
public class PairedDevicesDialogFragment extends DialogFragment implements DeviceConnectionStateListener
{
    public static final String DEVICE_ARG = "DEVICE_ARG";
    public final static String DEVICE_ADDRESS_EXTRA = "device.address";

    private PairedDeviceListAdapter mListAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState)
    {
        setRetainInstance(true);

        DeviceManager deviceManager = DeviceManager.getDeviceManager();
        List<Device> devices = new ArrayList<>(
            Objects.requireNonNull(deviceManager.getPairedDevices()));

        View view = inflater.inflate(R.layout.fragment_paired_devices, container, false);

        ListView mPairedDevicesList = view.findViewById(R.id.paired_devices_listview);
        mListAdapter = new PairedDeviceListAdapter(getContext(), devices, new DeviceItemClickListener());
        mPairedDevicesList.setAdapter(mListAdapter);

        //On clicking the paired device, transition to the device sync screen
        mPairedDevicesList.setOnItemClickListener((adapterView, v, i, l) -> {
            Device device = (Device)adapterView.getItemAtPosition(i);
            RealTimeDataHandler.getInstance().setDevice(device);
            Bundle bundle = new Bundle();
            bundle.putString(DEVICE_ARG, device.address());
            Intent intent = new Intent(PairedDevicesDialogFragment.this.getActivity(), DataDisplayActivity.class);
            intent.putExtra(DEVICE_ADDRESS_EXTRA, device.address());
            startActivity(intent);
        });

        mPairedDevicesList.setOnItemLongClickListener((adapterView, view1, i, l) -> {
            Device device = (Device)adapterView.getItemAtPosition(i);
            if(device.connectionState() != ConnectionState.CONNECTED) {
                Toast.makeText(getContext(), "Device Not Connected", Toast.LENGTH_SHORT).show();
                return false;
            }

            if(device.setupState() != SetupState.COMPLETE) {
                Toast.makeText(getContext(), "Device Setup Incomplete", Toast.LENGTH_SHORT).show();
                return false;
            }

            DeviceDetailDialogFragment frag = new DeviceDetailDialogFragment();
            Bundle args = new Bundle();
            args.putString(DEVICE_ADDRESS_EXTRA, device.address());
            frag.setArguments(args);

            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            transaction.replace(R.id.main_root, frag, frag.getTag()).addToBackStack(getTag()).commit();

            return true;
        });

        FloatingActionButton mAddDeviceButton = view.findViewById(R.id.add_device_button);
        mAddDeviceButton.setOnClickListener(mAddButtonListener);

        // if there are no paired devices, show an alert to begin the device scan
        if(devices.isEmpty())
        {
            ConfirmationDialog scanningConfirm = new ConfirmationDialog(getContext(), getString(R.string.scan_devices_title), getString(R.string.scan_devices_msg), getString(R.string.alert_dialog_ok),
                            getString(R.string.alert_dialog_cancel), new ScanningBeginClickListener());
            scanningConfirm.show();
        }

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();

        DeviceManager.getDeviceManager().addConnectionStateListener(this);

        if (actionBar != null)
        {
            actionBar.setTitle(R.string.connected_devices_title);
        }
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    private View.OnClickListener mAddButtonListener = view ->
    {
        //Transitions to a new fragment to begin the Bluetooth scanning for the Garmin devices nearby
        ScanningDialogFragment scanningDialogFragment = new ScanningDialogFragment();

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

        transaction.replace(R.id.main_root, scanningDialogFragment, scanningDialogFragment.getTag()).addToBackStack(getTag()).commit();
    };

    @Override
    public void onDeviceConnected(@NonNull Device device)
    {
        String deviceDetails = MapperUtil.toJson(device);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceDisconnected(@NonNull Device device)
    {
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceConnectionFailed(@NonNull Device device, @NonNull FailureCode failure)
    {
        Context context = getContext();

        if(context != null)
        {
            Toast.makeText(getContext(), String.format("Device Connection Failed. [%s]", device.address()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Listens to scanning begin click
     */
    private class ScanningBeginClickListener implements DialogInterface.OnClickListener
    {
        @Override
        public void onClick(DialogInterface dialogInterface, int i)
        {
            if(i == DialogInterface.BUTTON_POSITIVE)
            {
                ScanningDialogFragment scanningDialogFragment = new ScanningDialogFragment();

                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

                transaction.replace(R.id.main_root, scanningDialogFragment, scanningDialogFragment.getTag()).addToBackStack(getTag()).commit();
            }
        }
    }

    /**
     * Listener to handle the various click actions on device list item.
     */
    private class DeviceItemClickListener implements PairedDeviceListAdapter.OnItemClickListener
    {
        @Override
        public void onForgetDeviceClick(final Device device)
        {
            Log.d(getTag(), "onForgetDeviceClick(device = " + device.friendlyName() + ")");
            ConfirmationDialog dialog = new ConfirmationDialog(getContext(), null, getString(R.string.connected_forget_device_message), getString(R.string.button_yes), getString(R.string.button_no),
                    (dialog1, which) ->
                    {
                        if (which == DialogInterface.BUTTON_POSITIVE)
                        {
                            DeviceManager.getDeviceManager().forget(device.address());
                            mListAdapter.removeDevice(device.address());
                        }
                    });
            dialog.show();
        }
    }
}
