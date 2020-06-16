/*
 * Copyright (c) 2013 Garmin International. All Rights Reserved.
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
 * Created by johnsongar on 9/23/2016.
 */
package org.radarbase.garmin.ui.settings.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.garmin.health.settings.ConnectIqItem;

import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.settings.util.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SwitchOptionsView<T> extends LinearLayout
{
    private List<SwitchView<T>> mSwitchViews;
    private TextView titleView;

    private OnLongClickListener mListener;

    public SwitchOptionsView(Context context)
    {
        super(context);
        init();
    }

    public SwitchOptionsView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public SwitchOptionsView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void initialize()
    {
        initialize(-1);
    }

    public void initialize(@StringRes int title)
    {
        if (title != -1)
        {
            titleView.setText(title);
        }
        else
        {
            titleView.setVisibility(GONE);
        }
    }

    public void setOnSwitchLongPressListener(OnSwitchLongPressListener<T> listener)
    {
        OnLongClickListener list = view ->
        {
            if(((SwitchView<T>)view).isChecked())
            {
                listener.onLongPress(((SwitchView<T>)view).value());
                return true;
            }
            else
            {
                return false;
            }
        };

        for(SwitchView<T> switchView : mSwitchViews)
        {
            switchView.setOnLongClickListener(list);
        }

        mListener = list;
    }

    public void setEnabledOptions(Collection<T> enabledOptions, Collection<T> availableOptions) {

        LayoutParams layoutParams = configSwitchLayoutParams();
        LayoutParams dividerParams = dividerLayoutParams();
        addView(createDivider(), dividerParams);

        this.mSwitchViews.clear();

        for(T option : availableOptions)
        {
            boolean isEnabled = enabledOptions.contains(option);

            SwitchView<T> configSwitchView = new SwitchView<>(getContext(), option);

            if(option instanceof String)
            {
                configSwitchView.setData(TextUtils.toTitleCase(((String)option).replace('_', ' ')), isEnabled);
            }
            else if(option instanceof ConnectIqItem)
            {
                configSwitchView.setData(TextUtils.toTitleCase(((ConnectIqItem)option).getName()), isEnabled);
            }
            else
            {
                configSwitchView.setData(TextUtils.toTitleCase(option.toString()), isEnabled);
            }

            configSwitchView.setOnLongClickListener(mListener);

            this.mSwitchViews.add(configSwitchView);

            addView(configSwitchView, layoutParams);
            addView(createDivider(), dividerParams);
        }
    }

    public Set<T> checkedOptions()
    {
        Set<T> options = new HashSet<>();

        for (SwitchView<T> switchView : mSwitchViews)
        {
            if (switchView.isChecked())
            {
                options.add(switchView.value());
            }
        }

        return options;
    }

    private void init()
    {
        setOrientation(VERTICAL);
        mSwitchViews = new ArrayList<>();
    }

    private LayoutParams configSwitchLayoutParams()
    {
        int marginTopBottom = (int) getResources().getDimension(R.dimen.activity_vertical_margin);

        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = layoutParams.bottomMargin = marginTopBottom;

        return layoutParams;
    }

    private LayoutParams dividerLayoutParams()
    {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private View createDivider()
    {
        View divider = new View(getContext());
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.divider_color));
        return divider;
    }

    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();
        inflate(getContext(), R.layout.view_switch_options, this);

        titleView = findViewById(R.id.config_text_title);
    }

    public interface OnSwitchLongPressListener<T>
    {
        void onLongPress(T t);
    }
}
