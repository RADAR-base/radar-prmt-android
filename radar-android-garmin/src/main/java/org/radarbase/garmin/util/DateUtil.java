package org.radarbase.garmin.util;


import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Locale;

/**
 * Created by morajkar on 2/20/2018.
 */

public class DateUtil {
    /**
     * Converts a timestamp to the date format used on this sample app
     * @param timestamp
     * @return
     */
    public static String formatTimestampToDate(long timestamp){
        Calendar calendar = Calendar.getInstance(Locale.ENGLISH);
        calendar.setTimeInMillis(timestamp * 1000);
        return DateFormat.format("MM-dd-yyy HH:mm:ss", calendar).toString();
    }
}
