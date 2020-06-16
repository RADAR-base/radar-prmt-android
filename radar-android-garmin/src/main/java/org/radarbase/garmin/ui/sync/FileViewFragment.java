package org.radarbase.garmin.ui.sync;

import android.content.Context;

import com.garmin.health.Device;

import org.radarbase.garmin.ui.FlavoredFragment;

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
 * Created by jacksoncol on 2019-10-25.
 */
class FileViewFragment extends FlavoredFragment
{
    // STUB!
    public static FileViewFragment getInstance(Context context, Device mDevice, boolean b1, boolean b)
    {
        return null;
    }

    // STUB!
    public static FileViewFragment getDefaultInstance(Context context)
    {
        return new FileViewFragment();
    }

    // STUB!
    @Override
    public boolean isImplemented()
    {
        return false;
    }

    // STUB!
    @Override
    protected int getLayoutId()
    {
        return 0;
    }

    public boolean shouldDisplay()
    {
        return false;
    }
}
