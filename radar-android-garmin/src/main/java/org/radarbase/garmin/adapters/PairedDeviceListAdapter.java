package org.radarbase.garmin.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.garmin.health.ConnectionState;
import com.garmin.health.Device;
import com.garmin.health.DeviceModel;

import org.radarbase.garmin.R;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class PairedDeviceListAdapter extends BaseAdapter
{
    private static final float IMAGE_SCREEN_PERCENT = .2f;
    private static final DisplayMetrics METRICS = Resources.getSystem().getDisplayMetrics();
    private static final int IMAGE_DIM = (int) (Math.min(METRICS.widthPixels, METRICS.heightPixels) * IMAGE_SCREEN_PERCENT);
    private Map<DeviceModel, Bitmap> mBitmapCache = new HashMap<>();

    private Context mContext;

    private List<Device> devices;

    private OnItemClickListener mOnItemClickListener;

    public PairedDeviceListAdapter(Context context, List<Device> devices, OnItemClickListener onItemClickListener)
    {
        this.devices = devices;
        this.mContext = context;
        this.mOnItemClickListener = onItemClickListener;
    }

    private Set<String> syncingDevices = new HashSet<>();

    public void setSyncing(String address, boolean isSyncing)
    {
        if(isSyncing)
        {
            syncingDevices.add(address);
        }
        else
        {
            syncingDevices.remove(address);
        }
    }

    @Override
    public boolean isEnabled(int position)
    {
        return true;
    }

    @Override
    public int getCount()
    {
        return devices.size();
    }

    @Override
    public Device getItem(int i)
    {
        return devices.get(i);
    }

    @Override
    public long getItemId(int i)
    {
        return i;
    }

    @Override
    public View getView(final int i, View view, ViewGroup viewGroup)
    {
        ViewHolder viewHolder;

        if(view == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = layoutInflater.inflate(R.layout.view_paired_device_row, viewGroup, false);

            viewHolder = new ViewHolder(view);

            view.setTag(viewHolder);
        }
        else
        {
            viewHolder = (ViewHolder) view.getTag();
        }

        Device device = devices.get(i);

        if(device != null)
        {
            viewHolder.deviceIcon.setImageBitmap(getDeviceImage(device.model()));
            viewHolder.friendlyName.setText(device.friendlyName());
            viewHolder.address.setText(device.address());
            final Device device1 = device;
            viewHolder.mForgetDevice.setOnClickListener(view1 -> mOnItemClickListener.onForgetDeviceClick(device1));
            viewHolder.toggleAnimator(syncingDevices.contains(device.address()));
            viewHolder.deviceIcon.setAlpha(device.connectionState() == ConnectionState.CONNECTED ? 1.0f : 0.5f);
        }

        view.setClickable(false);
        view.setFocusable(false);

        return view;
    }

    public void setDeviceList(Collection<Device> devices)
    {
        this.devices.clear();
        this.devices.addAll(devices);

        notifyDataSetChanged();
    }

    private Bitmap getDeviceImage(DeviceModel deviceModel)
    {
        if(!mBitmapCache.containsKey(deviceModel))
        {
            Resources resources = mContext.getResources();

            Bitmap bitmap = BitmapFactory
                    .decodeResource(resources, DeviceModel.getDeviceImage(deviceModel));

            mBitmapCache.put(
                    deviceModel,
                    Bitmap.createScaledBitmap(bitmap, IMAGE_DIM, IMAGE_DIM, false));
        }

        return mBitmapCache.get(deviceModel);

    }

    private static class ViewHolder
    {
        private TextView friendlyName;
        private TextView address;
        private ImageView deviceIcon;
        private ImageView mForgetDevice;

        private AlphaAnimation syncAnimator;

        ViewHolder(View rowView)
        {
            friendlyName = rowView.findViewById(R.id.device_friendly_name);
            address = rowView.findViewById(R.id.device_uuid);
            deviceIcon = rowView.findViewById(R.id.device_icon);
            mForgetDevice = rowView.findViewById(R.id.device_forget_device);

            syncAnimator = new AlphaAnimation(0.1f, 1.0f);
            syncAnimator.setDuration(1000);
            syncAnimator.setFillAfter(true);
            syncAnimator.setRepeatCount(Animation.INFINITE);
            syncAnimator.setRepeatMode(Animation.REVERSE);

            friendlyName.setClickable(false);
            friendlyName.setFocusable(false);

            address.setClickable(false);
            address.setFocusable(false);

            deviceIcon.setClickable(false);
            deviceIcon.setFocusable(false);
        }

        void toggleAnimator(boolean toggle)
        {
            if(!toggle)
            {
                deviceIcon.clearAnimation();
            }
            else
            {
                deviceIcon.clearAnimation();
                deviceIcon.startAnimation(syncAnimator);
            }
        }
    }

    private Device getDevice(String address)
    {
        for(Device device : devices)
        {
            if (device.address().equals(address))
            {
                return device;
            }
        }

        return null;
    }

    public void removeDevice(String macAddress)
    {
        Device connectedDevice = getDevice(macAddress);

        if(connectedDevice != null)
        {
            devices.remove(connectedDevice);
            notifyDataSetChanged();
        }
    }

    public interface OnItemClickListener
    {
        void onForgetDeviceClick(Device device);
    }
}
