package org.radarcns;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class RadarConfiguration {
    public static final String RADAR_PREFIX = "org.radarcns.android.";

    public static final String KAFKA_REST_PROXY_URL_KEY = "kafka_rest_proxy_url";
    public static final String SCHEMA_REGISTRY_URL_KEY = "schema_registry_url";
    public static final String DEVICE_GROUP_ID_KEY = "device_group_id";
    public static final String EMPATICA_API_KEY = "empatica_api_key";
    public static final String UI_REFRESH_RATE_KEY = "ui_refresh_rate_millis";
    public static final String KAFKA_UPLOAD_RATE_KEY = "kafka_upload_rate";
    public static final String DATABASE_COMMIT_RATE_KEY = "database_commit_rate";
    public static final String KAFKA_CLEAN_RATE_KEY = "kafka_clean_rate";
    public static final String KAFKA_RECORDS_SEND_LIMIT_KEY = "kafka_records_send_limit";
    public static final String SENDER_CONNECTION_TIMEOUT_KEY = "sender_connection_timeout";
    public static final String DATA_RETENTION_KEY = "data_retention_ms";
    public static final String FIREBASE_FETCH_TIMEOUT_KEY = "firebase_fetch_timeout";
    public static final String CONDENSED_DISPLAY_KEY = "is_condensed_n_records_display";

    public static final Pattern IS_TRUE = Pattern.compile(
            "^(1|true|t|yes|y|on)$", CASE_INSENSITIVE);
    public static final Pattern IS_FALSE = Pattern.compile(
            "^(0|false|f|no|n|off|)$", CASE_INSENSITIVE);

    public static final Set<String> LONG_VALUES = new HashSet<>(Arrays.asList(
            UI_REFRESH_RATE_KEY, KAFKA_UPLOAD_RATE_KEY, DATABASE_COMMIT_RATE_KEY,
            KAFKA_CLEAN_RATE_KEY, SENDER_CONNECTION_TIMEOUT_KEY, DATA_RETENTION_KEY,
            FIREBASE_FETCH_TIMEOUT_KEY));

    public static final Set<String> INT_VALUES = Collections.singleton(
            KAFKA_RECORDS_SEND_LIMIT_KEY);

    public static final Set<String> BOOLEAN_VALUES = Collections.singleton(
            CONDENSED_DISPLAY_KEY);

    private static final Object syncObject = new Object();
    private static RadarConfiguration instance = null;
    private final FirebaseRemoteConfig config;

    public static final long FIREBASE_FETCH_TIMEOUT_DEFAULT = 43200L;
    private final Handler handler;
    private Activity onFetchActivity;
    private OnCompleteListener<Void> onFetchCompleteHandler;

    private RadarConfiguration(@NonNull FirebaseRemoteConfig config) {
        this.config = config;
        this.onFetchCompleteHandler = null;

        this.handler = new Handler();
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetch();
                long delay = getLong(FIREBASE_FETCH_TIMEOUT_KEY, FIREBASE_FETCH_TIMEOUT_DEFAULT);
                handler.postDelayed(this, delay);
            }
        }, getLong(FIREBASE_FETCH_TIMEOUT_KEY, FIREBASE_FETCH_TIMEOUT_DEFAULT));
    }

    public FirebaseRemoteConfig getFirebase() {
        return config;
    }

    public boolean isInDevelopmentMode() {
        return config.getInfo().getConfigSettings().isDeveloperModeEnabled();
    }

    public static RadarConfiguration getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                throw new IllegalStateException("RadarConfiguration instance is not yet "
                        + "initialized");
            }
            return instance;
        }
    }

    public static RadarConfiguration configure(boolean inDevelopmentMode, int defaultSettings) {
        synchronized (syncObject) {
            if (instance == null) {
                FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(inDevelopmentMode)
                        .build();
                FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
                config.setConfigSettings(configSettings);
                config.setDefaults(defaultSettings);

                instance = new RadarConfiguration(config);
            }
            return instance;
        }
    }

    public Task<Void> fetch() {
        long delay;
        if (isInDevelopmentMode()) {
            delay = 0L;
        } else {
            delay = getLong(FIREBASE_FETCH_TIMEOUT_KEY, FIREBASE_FETCH_TIMEOUT_DEFAULT);
        }
        return fetch(delay);
    }

    private Task<Void> fetch(long delay) {
        Task<Void> task = config.fetch(delay);
        synchronized (this) {
            if (onFetchCompleteHandler != null) {
                if (onFetchActivity != null) {
                    task.addOnCompleteListener(onFetchActivity, onFetchCompleteHandler);
                } else {
                    task.addOnCompleteListener(onFetchCompleteHandler);
                }
            }
        }
        return task;
    }

    public Task<Void> forceFetch() {
        return fetch(0L);
    }

    public synchronized void onFetchComplete(Activity activity, OnCompleteListener<Void> completeListener) {
        onFetchActivity = activity;
        onFetchCompleteHandler = completeListener;
    }

    public boolean activateFetched() {
        return config.activateFetched();
    }

    public String getString(@NonNull String key) {
        String result = config.getString(key);

        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("Key does not have a value");
        }

        return result;
    }

    public String getString(@NonNull String key, String defaultValue) {
        String result = config.getString(key);

        if (result == null || result.isEmpty()) {
            return defaultValue;
        }

        return result;
    }

    /**
     * Get a configured long value.
     * @param key key of the value
     * @return long value
     * @throws NumberFormatException if the configured value is not a Long
     * @throws IllegalArgumentException if the key does not have an associated value
     */
    public long getLong(@NonNull String key) {
        return Long.parseLong(getString(key));
    }

    /**
     * Get a configured int value.
     * @param key key of the value
     * @return int value
     * @throws NumberFormatException if the configured value is not an Integer
     * @throws IllegalArgumentException if the key does not have an associated value
     */
    public int getInt(@NonNull String key) {
        return Integer.parseInt(getString(key));
    }

    /**
     * Get a configured long value. If the configured value is not present or not a valid long,
     * return a default value.
     * @param key key of the value
     * @param defaultValue default value
     * @return configured long value, or defaultValue if no suitable value was found.
     */
    public long getLong(@NonNull String key, long defaultValue) {
        try {
            String result = config.getString(key);
            if (result != null && !result.isEmpty()) {
                return Long.parseLong(result);
            }
        } catch (NumberFormatException ex) {
            // return default
        }
        return defaultValue;
    }

    /**
     * Get a configured long value. If the configured value is not present or not a valid long,
     * return a default value.
     * @param key key of the value
     * @param defaultValue default value
     * @return configured long value, or defaultValue if no suitable value was found.
     */
    public int getInt(@NonNull String key, int defaultValue) {
        try {
            String result = config.getString(key);
            if (result != null && !result.isEmpty()) {
                return Integer.parseInt(result);
            }
        } catch (NumberFormatException ex) {
            // return default
        }
        return defaultValue;
    }

    public boolean containsKey(@NonNull String key) {
        return config.getKeysByPrefix(key).contains(key);
    }

    public boolean getBoolean(@NonNull String key) {
        String str = getString(key);
        if (IS_TRUE.matcher(str).find()) {
            return true;
        } else if (IS_FALSE.matcher(str).find()) {
            return false;
        } else {
            throw new NumberFormatException("String '" + str + "' of property '" + key
                    + "' is not a boolean");
        }
    }


    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        String str = getString(key, null);
        if (str == null) {
            return defaultValue;
        }
        if (IS_TRUE.matcher(str).find()) {
            return true;
        } else if (IS_FALSE.matcher(str).find()) {
            return false;
        } else {
            return defaultValue;
        }
    }

    public Set<String> keySet() {
        Set<String> baseKeys = new HashSet<>(config.getKeysByPrefix(null));
        Iterator<String> iter = baseKeys.iterator();
        while (iter.hasNext()) {
            if (getString(iter.next(), null) == null) {
                iter.remove();
            }
        }
        return baseKeys;
    }

    public boolean equals(Object obj) {
        return obj != null
                && !obj.getClass().equals(getClass())
                && config.equals(((RadarConfiguration) obj).config);
    }

    public int hashCode() {
        return config.hashCode();
    }

    public void putExtras(Bundle bundle, String... extras) {
        for (String extra : extras) {
            try {
                if (LONG_VALUES.contains(extra)) {
                    bundle.putLong(RADAR_PREFIX + extra, getLong(extra));
                } else if (INT_VALUES.contains(extra)) {
                    bundle.putInt(RADAR_PREFIX + extra, getInt(extra));
                } else {
                    bundle.putString(RADAR_PREFIX + extra, getString(extra));
                }
            } catch (IllegalArgumentException ex) {
                // do nothing
            }
        }
    }

    public static boolean hasExtra(Bundle bundle, String key) {
        return bundle.containsKey(RADAR_PREFIX + key);
    }

    public static int getIntExtra(Bundle bundle, String key, int defaultValue) {
        return bundle.getInt(RADAR_PREFIX + key, defaultValue);
    }

    public static int getIntExtra(Bundle bundle, String key) {
        return bundle.getInt(RADAR_PREFIX + key);
    }

    public static long getLongExtra(Bundle bundle, String key, long defaultValue) {
        return bundle.getLong(RADAR_PREFIX + key, defaultValue);
    }

    public static long getLongExtra(Bundle bundle, String key) {
        return bundle.getLong(RADAR_PREFIX + key);
    }

    public static String getStringExtra(Bundle bundle, String key, String defaultValue) {
        return bundle.getString(RADAR_PREFIX + key, defaultValue);
    }

    public static String getStringExtra(Bundle bundle, String key) {
        return bundle.getString(RADAR_PREFIX + key);
    }
}
