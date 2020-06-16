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
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.radarbase.garmin.R;


public class SwitchView<T> extends RelativeLayout
{
    protected TextView textView;
    protected Switch switchView;

    private T value;

    public SwitchView(Context context)
    {
        super(context);
        inflateViews();
    }

    public SwitchView(Context context, T value)
    {
        super(context);
        this.value = value;
        inflateViews();
    }

    public SwitchView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public SwitchView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public void setData(String textData, boolean isChecked)
    {
        textView.setText(textData);
        switchView.setChecked(isChecked);
    }

    public T value()
    {
        return this.value;
    }

    public boolean isChecked()
    {
        return switchView.isChecked();
    }

    private void inflateViews()
    {
        inflate(getContext(), R.layout.view_switch_item, this);

        textView = findViewById(R.id.config_switch_view_text);
        switchView = findViewById(R.id.config_switch_view_switch);
    }
}