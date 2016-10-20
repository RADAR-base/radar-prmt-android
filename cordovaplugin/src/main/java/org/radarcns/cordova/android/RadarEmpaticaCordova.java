package org.radarcns.cordova.android;

import org.radarcns.empaticaE4.E4Topics;

import java.io.IOException;

public class RadarEmpaticaCordova extends CordovaPlugin {
    private E4Topics topics;
    protected void pluginInitialize() {
        try {
            topics = E4Topics.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
