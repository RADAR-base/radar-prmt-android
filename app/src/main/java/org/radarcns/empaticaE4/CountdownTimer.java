package org.radarcns.empaticaE4;

/**
 * Created by Maxim on 13-09-16.
 */
public class CountdownTimer {

    private long mStart;
    private final long mCountdownTime;

    public CountdownTimer(long countDownTime) {
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
