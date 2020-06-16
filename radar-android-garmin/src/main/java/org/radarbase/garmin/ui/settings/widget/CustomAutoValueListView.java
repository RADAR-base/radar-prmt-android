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
import android.graphics.Color;
import android.text.InputType;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import org.radarbase.garmin.R;
import org.radarbase.garmin.ui.settings.util.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomAutoValueListView<T> extends LinearLayout
{
    private Map<T, CustomAutoValueView> mValueViews;
    private Class<T> mClazz;
    private TextView mTitleView;

    private Set<T> mItemsWithValue = null;

    public CustomAutoValueListView(Context context)
    {
        super(context);
        init();
    }

    public CustomAutoValueListView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public CustomAutoValueListView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void initialize(Class<T> clazz)
    {
        initialize(clazz, -1);
    }

    public void initialize(Class<T> clazz, @StringRes int title)
    {
        this.mClazz = clazz;

        if(title != -1)
        {
            mTitleView.setText(title);
        }
        else
        {
            mTitleView.setVisibility(GONE);
        }
    }

    public void initialize(Class<T> clazz, Set<T> valueCarryingSet, @StringRes int title)
    {
        initialize(clazz, title);

        mItemsWithValue = valueCarryingSet;
    }

    public void setOptions(Map<T, Float> options)
    {
        LayoutParams layoutParams = configLayoutParams();

        this.mValueViews.clear();

        for(T option : options.keySet())
        {
            CustomAutoValueView autoValueView = new CustomAutoValueView(getContext());
            String input = TextUtils.toTitleCase((option instanceof String) ? (String)option : option.toString());

            if(options.get(option) == null)
            {
                autoValueView.setTitle(input + " ERROR");
                autoValueView.setTitleColor(Color.MAGENTA);
            }
            else
            {
                autoValueView.setTitle(input);
            }

            autoValueView.setValue(options.get(option));

            autoValueView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

            if(mItemsWithValue != null && !mItemsWithValue.contains(option))
            {
                autoValueView.setValueEnabled(false);
            }

            this.mValueViews.put(option, autoValueView);

            addView(autoValueView, layoutParams);
        }
    }

    public Map<T, Float> getOptions()
    {
        Map<T, Float> options = new HashMap<>();

        for(T key : mValueViews.keySet())
        {
            CustomAutoValueView view = mValueViews.get(key);
            options.put(key, view.getValue());
        }

        return options;
    }

    public Map<T, Float> getCheckedOptions()
    {
        Map<T, Float> options = new HashMap<>();

        for(T key : mValueViews.keySet())
        {
            CustomAutoValueView view = mValueViews.get(key);

            if(view.isChecked())
            {
                options.put(key, view.getValue());
            }
        }

        return options;
    }

    private void init()
    {
        setOrientation(VERTICAL);
        mValueViews = new ArrayMap<>();
    }

    private LayoutParams configLayoutParams()
    {
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = layoutParams.bottomMargin = (int)getResources().getDimension(R.dimen.medium_vertical_margin);

        return layoutParams;
    }

    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();
        inflate(getContext(), R.layout.view_switch_options, this);

        mTitleView = findViewById(R.id.config_text_title);
    }
}
