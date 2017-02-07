package org.radarcns.util;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class ApplicationSourceId {
    private static final String SOURCE_ID_KEY = "source.id";

    /**
     * Load source id from file. If not yet stored, create new
     * id and persist to phone storage
     * @param clazz
     * @return
     */
    public static String getSourceIdFromFile(Class<?> clazz) {
        Properties defaults = new Properties();
        defaults.setProperty(SOURCE_ID_KEY, UUID.randomUUID().toString());
        try {
            Properties props = PersistentStorage.loadOrStore(clazz, defaults);
            return props.getProperty(SOURCE_ID_KEY);
        } catch (IOException ex) {
            // Use newly generated UUID
            return defaults.getProperty(SOURCE_ID_KEY);
        }
    }
}
