package com.empatica.sample;

/**
 * Created by Maxim on 13-09-16.
 */
public class Time {

    private long mStart;
    private long mCountdownTime;

    public Time() {
        this(0);
    }

    public Time(long countDownTime) {
        mCountdownTime = countDownTime;
        mStart = System.currentTimeMillis();
    }

    // Return runtime in milliseconds
    public long getMillis() {
        long currentTime = System.currentTimeMillis();
        return currentTime - mStart;
    }

    // Return runtime in seconds
    public int getSeconds() {
        return (int) getMillis()/1000;
    }

    // Return remaining time in milliseconds
    public long getRemainingMillis() {
        return mCountdownTime - getMillis();
    }

    // Return remaining time in seconds
    public int getRemainingSeconds() {
        return (int) getRemainingMillis()/1000;
    }
}
