package org.radarbase.garmin.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import com.garmin.health.AbstractGarminHealth;
import com.garmin.health.GarminHealth;
import com.garmin.health.GarminHealthInitializationException;
import org.radarbase.garmin.R;

/**
 * Copyright (c) 2017 Garmin International. All Rights Reserved.
 * <p/>
 * This software is the confidential and proprietary information of
 * Garmin International.
 * You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement
 * you entered into with Garmin International.
 * <p/>
 * Garmin International MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. Garmin International SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * <p/>
 * Created by jacksoncol on 2019-12-02.
 */
public class GarminHealthServiceReceiver extends BroadcastReceiver
{
    public static final IntentFilter INTENT_FILTER;

    static
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AbstractGarminHealth.ACTION_SERVICE_BINDING_DIED);
        filter.addAction(AbstractGarminHealth.ACTION_SERVICE_PROCESS_DIED);
        INTENT_FILTER = new IntentFilter(filter);
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(intent.getAction() != null)
        {
            switch(intent.getAction())
            {
                case AbstractGarminHealth
                        .ACTION_SERVICE_BINDING_DIED:
                    Toast.makeText(context, "Garmin Health service binding died unexpectedly.", Toast.LENGTH_LONG).show();
                    GarminHealth.restart();
                    break;
                case AbstractGarminHealth
                        .ACTION_SERVICE_PROCESS_DIED:
                    Toast.makeText(context, "Garmin Health service process died unexpectedly.", Toast.LENGTH_LONG).show();
                    try {
                        GarminHealth.initialize(context, context.getString(R.string.companion_license));
                    }
                    catch (GarminHealthInitializationException ignored) {}
                    break;
            }
        }
    }
}
