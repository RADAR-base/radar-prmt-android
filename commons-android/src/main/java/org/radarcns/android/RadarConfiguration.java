package org.radarcns.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class RadarConfiguration {
    public static final String RADAR_PREFIX = "org.radarcns.android.";

    public static final String KAFKA_REST_PROXY_URL_KEY = "kafka_rest_proxy_url";
    public static final String SCHEMA_REGISTRY_URL_KEY = "schema_registry_url";
    public static final String DEFAULT_GROUP_ID_KEY = "default_group_id";
    public static final String SOURCE_ID_KEY = "source_id";
    public static final String EMPATICA_API_KEY = "empatica_api_key";
    public static final String UI_REFRESH_RATE_KEY = "ui_refresh_rate_millis";
    public static final String KAFKA_UPLOAD_RATE_KEY = "kafka_upload_rate";
    public static final String DATABASE_COMMIT_RATE_KEY = "database_commit_rate";
    public static final String KAFKA_CLEAN_RATE_KEY = "kafka_clean_rate";
    public static final String KAFKA_RECORDS_SEND_LIMIT_KEY = "kafka_records_send_limit";
    public static final String SENDER_CONNECTION_TIMEOUT_KEY = "sender_connection_timeout";
    public static final String DATA_RETENTION_KEY = "data_retention_ms";
    public static final String FIREBASE_FETCH_TIMEOUT_MS_KEY = "firebase_fetch_timeout_ms";
    public static final String CONDENSED_DISPLAY_KEY = "is_condensed_n_records_display";
    public static final String START_AT_BOOT = "start_at_boot";
    public static final String DEVICE_SERVICES_TO_CONNECT = "device_services_to_connect";

    public static final Pattern IS_TRUE = Pattern.compile(
            "^(1|true|t|yes|y|on)$", CASE_INSENSITIVE);
    public static final Pattern IS_FALSE = Pattern.compile(
            "^(0|false|f|no|n|off|)$", CASE_INSENSITIVE);

    public FirebaseStatus getStatus() {
        return status;
    }

    public enum FirebaseStatus {
        UNAVAILABLE, ERROR, READY, FETCHING, FETCHED
    }

    public static final Set<String> LONG_VALUES = new HashSet<>(Arrays.asList(
            UI_REFRESH_RATE_KEY, KAFKA_UPLOAD_RATE_KEY, DATABASE_COMMIT_RATE_KEY,
            KAFKA_CLEAN_RATE_KEY, SENDER_CONNECTION_TIMEOUT_KEY, DATA_RETENTION_KEY,
            FIREBASE_FETCH_TIMEOUT_MS_KEY));

    public static final Set<String> INT_VALUES = Collections.singleton(
            KAFKA_RECORDS_SEND_LIMIT_KEY);

    public static final Set<String> BOOLEAN_VALUES = Collections.singleton(
            CONDENSED_DISPLAY_KEY);

    private static final Object syncObject = new Object();
    private static RadarConfiguration instance = null;
    private final FirebaseRemoteConfig config;
    private FirebaseStatus status;

    public static final long FIREBASE_FETCH_TIMEOUT_MS_DEFAULT = 12*60*60 * 1000L;
    private final Handler handler;
    private Activity onFetchActivity;
    private OnCompleteListener<Void> onFetchCompleteHandler;
    private final Map<String, Object> localConfiguration;

    private RadarConfiguration(@NonNull Context context, @NonNull FirebaseRemoteConfig config) {
        this.config = config;
        this.onFetchCompleteHandler = null;

        this.localConfiguration = new ConcurrentHashMap<>();
        this.handler = new Handler();

        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        if (googleApi.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            status = FirebaseStatus.READY;
            this.handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fetch();
                    long delay = getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT);
                    handler.postDelayed(this, delay);
                }
            }, getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT));
        } else {
            status = FirebaseStatus.UNAVAILABLE;
        }
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

    public static RadarConfiguration configure(@NonNull Context context, boolean inDevelopmentMode, int defaultSettings) {
        synchronized (syncObject) {
            if (instance == null) {
                FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(inDevelopmentMode)
                        .build();
                FirebaseRemoteConfig config = FirebaseRemoteConfig.getInstance();
                config.setConfigSettings(configSettings);
                config.setDefaults(defaultSettings);

                instance = new RadarConfiguration(context, config);
            }
            return instance;
        }
    }

    public Object put(String key, Object value) {
        return localConfiguration.put(key, value);
    }

    /**
     * Fetch the configuration from the firebase server.
     * @return fetch task or null status is {@link FirebaseStatus#UNAVAILABLE}.
     */
    public Task<Void> fetch() {
        long delay;
        if (isInDevelopmentMode()) {
            delay = 0L;
        } else {
            delay = getLong(FIREBASE_FETCH_TIMEOUT_MS_KEY, FIREBASE_FETCH_TIMEOUT_MS_DEFAULT);
        }
        return fetch(delay);
    }

    /**
     * Fetch the configuration from the firebase server.
     * @param delay
     * @return fetch task or null status is {@link FirebaseStatus#UNAVAILABLE}.
     */
    private Task<Void> fetch(long delay) {
        if (status == FirebaseStatus.UNAVAILABLE) {
            return null;
        }
        Task<Void> task = config.fetch(delay);
        synchronized (this) {
            status = FirebaseStatus.FETCHING;
            task.addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    synchronized (RadarConfiguration.this) {
                        if (task.isSuccessful()) {
                            status = FirebaseStatus.FETCHED;
                        } else {
                            status = FirebaseStatus.ERROR;
                        }
                    }
                }
            });
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

    private String getRawString(String key) {
        if (localConfiguration.containsKey(key)) {
            Object value = localConfiguration.get(key);
            if (value == null || value instanceof String) {
                return (String)value;
            } else {
                return null;
            }
        } else {
            return config.getString(key);
        }
    }

    private Long getRawLong(String key) {
        if (localConfiguration.containsKey(key)) {
            Object value = localConfiguration.get(key);
            if (value == null) {
                return null;
            } else if (value instanceof Number) {
                return ((Number)value).longValue();
            } else if (value instanceof String) {
                return Long.valueOf((String)value);
            } else {
                return null;
            }
        } else {
            return Long.valueOf(getString(key));
        }
    }

    private Integer getRawInteger(String key) {
        if (localConfiguration.containsKey(key)) {
            Object value = localConfiguration.get(key);
            if (value == null) {
                return null;
            } else if (value instanceof Number) {
                return ((Number)value).intValue();
            } else if (value instanceof String) {
                return Integer.valueOf((String)value);
            } else {
                return null;
            }
        } else {
            return Integer.valueOf(getString(key));
        }
    }

    public String getString(@NonNull String key) {
        String result = getRawString(key);

        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("Key does not have a value");
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
        Long ret = getRawLong(key);
        if (ret == null) {
            throw new IllegalArgumentException("Key does not have a value");
        }
        return ret;
    }


    /**
     * Get a configured int value.
     * @param key key of the value
     * @return int value
     * @throws NumberFormatException if the configured value is not an Integer
     * @throws IllegalArgumentException if the key does not have an associated value
     */
    public int getInt(@NonNull String key) {
        Integer ret = getRawInteger(key);
        if (ret == null) {
            throw new IllegalArgumentException("Key does not have a value");
        }
        return ret;
    }

    public String getString(@NonNull String key, String defaultValue) {
        String result = getRawString(key);

        if (result == null || result.isEmpty()) {
            return defaultValue;
        }

        return result;
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
            Long ret = getRawLong(key);
            if (ret != null) {
                return ret;
            }
        } catch (IllegalArgumentException ex) {
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
            Integer ret = getRawInteger(key);
            if (ret != null) {
                return ret;
            }
        } catch (IllegalArgumentException ex) {
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
            String key = RADAR_PREFIX + extra;

            if (localConfiguration.containsKey(extra)) {
                Object value = localConfiguration.get(extra);
                if (value == null) {
                    bundle.putString(key, null);
                } else if (value instanceof Boolean) {
                    bundle.putBoolean(key, (Boolean) value);
                } else if (value instanceof Long) {
                    bundle.putLong(key, (Long) value);
                } else if (value instanceof Integer) {
                    bundle.putInt(key, (Integer) value);
                }
            }
            else {
                try {
                    if (LONG_VALUES.contains(extra)) {
                        bundle.putLong(key, getLong(extra));
                    } else if (INT_VALUES.contains(extra)) {
                        bundle.putInt(key, getInt(extra));
                    } else if (BOOLEAN_VALUES.contains(extra)) {
                        bundle.putBoolean(key, getString(extra).equals("true"));
                    } else {
                        bundle.putString(key, getString(extra));
                    }
                } catch (IllegalArgumentException ex) {
                    // do nothing
                }
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
