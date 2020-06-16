/*
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
 * Created by johnsongar on 2/3/2017.
 */
package org.radarbase.garmin.ui.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.StringRes;

import org.radarbase.garmin.R;


public class CustomAutoValueView extends LinearLayout {

    private int mInputType;

    private Switch mUseAutoSwitch;
    private EditText mCustomValueEdit;
    private boolean mValueEnabled = true;

    public CustomAutoValueView(Context context)
    {
        super(context);
        init(null);
    }

    public CustomAutoValueView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(attrs);
    }

    public CustomAutoValueView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs)
    {
        setOrientation(VERTICAL);

        if(attrs != null)
        {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CustomAutoValueView);
            mInputType = a.getInt(R.styleable.CustomAutoValueView_android_inputType, InputType.TYPE_CLASS_NUMBER);
            a.recycle();
        }

        inflateViews();
    }

    public void setTitle(String title) {
        mUseAutoSwitch.setText(title);
    }

    public void setTitle(@StringRes int title) {
        mUseAutoSwitch.setText(title);
    }

    public void setValue(Float value)
    {
        mUseAutoSwitch.setChecked(value != null && value != 0);
        mCustomValueEdit.setText(formatNumber(value));
    }

    public static String formatNumber(Float d)
    {
        if(d == null)
        {
            return "";
        }

        if(d == d.longValue())
            return String.format("%d",d.longValue());
        else
            return String.format("%.1f",d);
    }

    public float getValue()
    {
        String value = mCustomValueEdit.getText().toString();
        if (mUseAutoSwitch.isChecked() && !TextUtils.isEmpty(value))
        {
            return Float.parseFloat(value);
        }
        return 0;
    }

    private void inflateViews()
    {
        inflate(getContext(), R.layout.view_auto_value_item, this);

        mUseAutoSwitch = findViewById(R.id.auto_switch);
        mCustomValueEdit = findViewById(R.id.custom_value);
        mCustomValueEdit.setInputType(mInputType);

        mUseAutoSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
        {
            mCustomValueEdit.setVisibility(isChecked && mValueEnabled ? View.VISIBLE : View.GONE);
        });
    }

    public void setInputType(int inputType)
    {
        this.mInputType = inputType;

        if (mCustomValueEdit != null)
        {
            mCustomValueEdit.setInputType(mInputType);
        }
    }

    public void setValueEnabled(boolean valueEnabled)
    {
        mValueEnabled = valueEnabled;

        if(!mValueEnabled)
        {
            mCustomValueEdit.setVisibility(GONE);
        }
    }

    public boolean isChecked()
    {
        return mUseAutoSwitch.isChecked();
    }

    public void setTitleColor(int color)
    {
        mUseAutoSwitch.setTextColor(color);
    }
}
