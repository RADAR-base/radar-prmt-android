package org.radarbase.garmin.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.radarbase.garmin.R;
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
 * Created by jacksoncol on 7/6/17.
 */

public class LinkAdapter extends BaseAdapter
{
    private Context mContext;

    public LinkAdapter(Context context)
    {
        mContext = context;
    }

    @Override
    public int getCount()
    {
        return thumbnails.length;
    }

    @Override
    public Class getItem(int i)
    {
        return fragments[i];
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

        if(view == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = layoutInflater.inflate(R.layout.view_activity_link, viewGroup, false);

            viewHolder = new ViewHolder(view);

            view.setTag(viewHolder);
        }
        else
        {
            viewHolder = (ViewHolder) view.getTag();
        }

        viewHolder.imageView.setImageResource(thumbnails[i]);
        viewHolder.title.setText(labels[i]);

        view.setClickable(false);
        view.setFocusable(false);

        return view;
    }

    private static class ViewHolder
    {
        private ImageView imageView;
        private TextView title;

        public ViewHolder(View view)
        {
            imageView = view.findViewById(R.id.link_icon);
            title = view.findViewById(R.id.link_label);

            imageView.setClickable(false);
            imageView.setFocusable(false);

            title.setClickable(false);
            title.setFocusable(false);
        }
    }

    private Integer[] thumbnails = {
            R.drawable.ic_data_usage_black_24dp,
            R.drawable.ic_trending_up_black_24dp
    };

    private String[] labels = {
            "Wellness Goals",
            "Seamless View"
    };

    private Class[] fragments = {

    };
}
