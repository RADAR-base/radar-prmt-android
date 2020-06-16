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
 * Created by johnsongar on 3/10/2017.
 */
package org.radarbase.garmin.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConfirmationDialog {
    private final Context mContext;
    private final String mTitle;
    private final String mMessage;
    private final String mPositiveText;
    private final String mNegativeText;
    private final DialogInterface.OnClickListener mOnClickListener;
    private AlertDialog mAlertDialog;

    public ConfirmationDialog(@NonNull Context context, String title, String message, String positiveText) {
        this(context, title, message, positiveText, null, null);
    }

    public ConfirmationDialog(@NonNull Context context, String title, String message, String positiveText,
                              @Nullable DialogInterface.OnClickListener listener) {
        this(context, title, message, positiveText, null, listener);
    }

    public ConfirmationDialog(@NonNull Context context, String title, String message, String positiveText,
                              String negativeText, @Nullable DialogInterface.OnClickListener listener) {
        this.mContext = context;
        this.mTitle = title;
        this.mMessage = message;
        this.mPositiveText = positiveText;
        this.mNegativeText = negativeText;
        this.mOnClickListener = listener;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        if (!TextUtils.isEmpty(mTitle)) {
            builder.setTitle(mTitle);
        }
        if (!TextUtils.isEmpty(mMessage)) {
            builder.setMessage(mMessage);
        }
        if (!TextUtils.isEmpty(mPositiveText)) {
            builder.setPositiveButton(mPositiveText, mOnClickListener);
        }
        if (!TextUtils.isEmpty(mNegativeText)) {
            builder.setNegativeButton(mNegativeText, mOnClickListener);
        }

        this.mAlertDialog = builder.create();
        this.mAlertDialog.show();
    }

    public void cancel()
    {
        if(mAlertDialog != null)
        {
            mAlertDialog.cancel();
        }
    }
}