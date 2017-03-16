package org.radarcns.android.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.radarcns.producer.KafkaSender;

/**
 * Checks the connection of a sender.
 *
 * It does so using two mechanisms: a regular heartbeat signal when the connection is assumed to be
 * present, and a exponential back-off mechanism if the connection is severed. If the connection is
 * assessed to be present through another mechanism, {@link #didConnect()} should be called,
 * conversely, if it is assessed to be severed, {@link #didDisconnect(IOException)} shoud be
 * called.
 */
class KafkaConnectionChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConnectionChecker.class);

    private static final int INCREMENTAL_BACKOFF_SECONDS = 60;
    private static final int MAX_BACKOFF_SECONDS = 14400; // 4 hours
    private static final long HEARTBEAT_SECONDS = 60L;
    private final KafkaSender sender;
    private final ScheduledExecutorService executor;
    private final ServerStatusListener listener;
    private final AtomicBoolean isConnected;
    private final Random random;
    private Future<?> scheduledFuture;
    private long lastConnection;
    private int retries;

    KafkaConnectionChecker(KafkaSender sender, ScheduledExecutorService executor, ServerStatusListener listener) {
        this.sender = sender;
        this.executor = executor;
        isConnected = new AtomicBoolean(true);
        lastConnection = -1L;
        this.retries = 0;
        this.listener = listener;
        this.random = new Random();
        this.scheduledFuture = null;
    }

    /**
     * Check whether the connection was closed and try to reconnect.
     */
    @Override
    public void run() {
        if (!isConnected.get()) {
            if (sender.isConnected() || sender.resetConnection()) {
                updateFuture(null);
                didConnect();
                listener.updateServerStatus(ServerStatusListener.Status.CONNECTED);
                logger.info("Sender reconnected");
            } else {
                synchronized (this) {
                    retries++;
                    updateFuture(executor.schedule(this, nextWait(), TimeUnit.SECONDS));
                }
            }
        } else if (isConnected.get() && System.currentTimeMillis() - lastConnection > 15_000L) {
            if (sender.isConnected()) {
                didConnect();
            } else {
                didDisconnect(null);
            }
        }
    }

    /** Check the connection as soon as possible. */
    public void check() {
        executor.execute(this);
    }

    /** Signal that the sender successfully connected. */
    public synchronized void didConnect() {
        lastConnection = System.currentTimeMillis();
        isConnected.set(true);
        if (scheduledFuture == null) {
            scheduledFuture = executor.scheduleAtFixedRate(this, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        }
        retries = 0;
    }

    /**
     * Signal that the Kafka REST sender has disconnected.
     * @param ex exception the sender disconnected with, may be null
     */
    public void didDisconnect(IOException ex) {
        logger.warn("Sender is disconnected", ex);

        if (isConnected.compareAndSet(true, false)) {
            listener.updateServerStatus(ServerStatusListener.Status.DISCONNECTED);
            // try to reconnect immediately
            executor.execute(this);
        }
    }

    /** Whether the connection is currently assumed to be present. */
    public boolean isConnected() {
        return isConnected.get();
    }

    private synchronized void updateFuture(Future<?> future) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = future;
    }

    /** Return the next waiting period in seconds */
    private long nextWait() {
        int range = Math.min(INCREMENTAL_BACKOFF_SECONDS * (1 << retries - 1), MAX_BACKOFF_SECONDS);
        return Math.round(random.nextFloat() * range);
    }
}
