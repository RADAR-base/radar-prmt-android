package org.radarcns.android.util;

import android.support.annotation.NonNull;

import java.util.concurrent.ThreadFactory;

public class AndroidThreadFactory implements ThreadFactory {
    private final String name;
    private final int priority;

    /**
     * Create threads in Android with the correct priority.
     * @param name thread name
     * @param priority one of android.os.Process.THEAD_PRIORITY_*
     */
    public AndroidThreadFactory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    @Override
    public Thread newThread(@NonNull final Runnable r) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(priority);
                r.run();
            }
        }, name);
    }
}
