package org.radarcns.android.util;

import android.os.Bundle;

public class BundleSerialization {
    public static String bundleToString(Bundle bundle) {
        StringBuilder sb = new StringBuilder(bundle.size() * 40);
        sb.append('{');
        boolean first = true;
        for (String key : bundle.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(key).append(": ").append(bundle.get(key));
        }
        sb.append('}');
        return sb.toString();
    }
}
