package org.radarcns.util;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Get the average of a set of values collected in a sliding time window of fixed duration.
 *
 * At least one value is needed to get an average.
 */
public class RollingTimeAverage {
    private final long window;
    private TimeCount firstTime;
    private double total;
    private Deque<TimeCount> deque;

    /**
     * A rolling time average with a sliding time window of fixed duration.
     * @param timeWindowMillis duration of the time window.
     */
    public RollingTimeAverage(long timeWindowMillis) {
        this.window = timeWindowMillis;
        this.total = 0d;
        this.firstTime = null;
        this.deque = new LinkedList<>();
    }

    /** Whether values have already been added. */
    public boolean hasAverage() {
        return firstTime != null;
    }

    /** Add a new value. */
    public void add(double x) {
        if (firstTime == null) {
            firstTime = new TimeCount(x);
        } else {
            deque.addLast(new TimeCount(x));
        }
        total += x;
    }

    /**
     * Get the average value per second over a sliding time window of fixed size.
     *
     * It takes one value before the window started as a baseline, and adds all values in the
     * window. It then divides by the total time window from the first value (outside/before the
     * window) to the last value (at the end of the window).
     * @return average value per second
     */
    public double getAverage() {
        long now = System.currentTimeMillis();
        long currentWindowStart = now - window;

        if (!hasAverage()) {
            throw new IllegalStateException("Cannot get average without values");
        }

        while (!this.deque.isEmpty() && this.deque.getFirst().time < currentWindowStart) {
            total -= this.firstTime.value;
            this.firstTime = this.deque.removeFirst();
        }
        if (this.deque.isEmpty() || this.firstTime.time >= currentWindowStart) {
            return 1000d * total / (now - this.firstTime.time);
        } else {
            long time = this.deque.getLast().time - currentWindowStart;
            double removedValue = this.firstTime.value + this.deque.getFirst().value * (currentWindowStart - this.firstTime.time) / (this.deque.getFirst().time - firstTime.time);
            double value = (total - removedValue) / time;
            return 1000d * value;
        }
    }

    private static class TimeCount {
        private final double value;
        private final long time;

        TimeCount(double value) {
            this.value = value;
            this.time = System.currentTimeMillis();
        }
    }
}
