package org.radarbase.garmin.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.garmin.health.DeviceModel;
import com.garmin.health.ScannedDevice;

import org.radarbase.garmin.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class ScannedDeviceListAdapter extends BaseAdapter
{
    private static final float IMAGE_SCREEN_PERCENT = .2f;
    private static final DisplayMetrics METRICS = Resources.getSystem().getDisplayMetrics();
    private static final int IMAGE_DIM = (int)(Math.min(METRICS.widthPixels, METRICS.heightPixels) * IMAGE_SCREEN_PERCENT);

    private Map<DeviceModel, Bitmap> mBitmapCache = new HashMap<>();

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private List<ScannedDevice> mDevices;

    private boolean mAdapterClosed = true;

    public ScannedDeviceListAdapter(Context applicationContext)
    {
        mContext = applicationContext;
        mLayoutInflater = (LayoutInflater)applicationContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDevices = new ArrayList<>();
    }

    @Override
    public int getCount()
    {
        return mDevices.size();
    }

    @Override
    public Object getItem(int i)
    {
        return mDevices.get(i);
    }

    @Override
    public long getItemId(int i)
    {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup)
    {
        ViewHolder viewHolder;

        if (view == null) {
            view = mLayoutInflater.inflate(R.layout.view_scanned_device_row, viewGroup, false);

            viewHolder = new ViewHolder(view);

            view.setTag(viewHolder);
        }
        else {
            viewHolder = (ViewHolder)view.getTag();
        }

        ScannedDevice device = mDevices.get(i);

        if (device != null) {
            viewHolder.deviceIcon.setImageBitmap(getDeviceImage(device.deviceModel()));
            viewHolder.friendlyName.setText(device.friendlyName());
            viewHolder.address.setText(device.address());
        }

        return view;
    }

    public List<ScannedDevice> getList()
    {
        return mDevices;
    }

    public void setList(List<ScannedDevice> deviceList)
    {
        if (mAdapterClosed) {
            return;
        }

        mDevices = deviceList;
        notifyDataSetChanged();
    }

    public void open()
    {
        mAdapterClosed = false;
    }

    public void addDevice(ScannedDevice device)
    {
        if (mAdapterClosed) {
            return;
        }

        for (ScannedDevice mDevice : mDevices) {
            if (device.address().equals(mDevice.address())) {
                return;
            }
        }

        mDevices.add(device);
        notifyDataSetChanged();
    }

    public void removeDevice(ScannedDevice device)
    {
        for (ScannedDevice mDevice : mDevices) {
            if (mDevice.address().equals(device.address())) {
                mDevices.remove(mDevice);
                notifyDataSetChanged();
                return;
            }
        }
    }

    public void close()
    {
        mAdapterClosed = true;

        mDevices.clear();
        notifyDataSetChanged();
    }

    private Bitmap getDeviceImage(DeviceModel deviceModel)
    {
        if (!mBitmapCache.containsKey(deviceModel)) {
            Resources resources = mContext.getResources();

            Bitmap bitmap = BitmapFactory
                    .decodeResource(resources, DeviceModel.getDeviceImage(deviceModel));

            if (bitmap != null) {
                mBitmapCache.put(
                        deviceModel,
                        Bitmap.createScaledBitmap(bitmap, IMAGE_DIM, IMAGE_DIM, false));
            }
        }

        return mBitmapCache.get(deviceModel);

    }

    private static class ViewHolder {
        private TextView friendlyName;
        private TextView address;
        private ImageView deviceIcon;

        ViewHolder(View rowView)
        {
            friendlyName = rowView.findViewById(R.id.device_friendly_name);
            address = rowView.findViewById(R.id.device_uuid);
            deviceIcon = rowView.findViewById(R.id.device_icon);
        }
    }
}
