package org.radarcns.util;

import android.os.Bundle;

public class Serialization {
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

    public static long bytesToLong(byte[] b, int startIndex) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i + startIndex] & 0xFF);
        }
        return result;
    }

    public static int bytesToInt(byte[] b, int startIndex) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= (b[i + startIndex] & 0xFF);
        }
        return result;
    }

    public static short bytesToShort(byte[] b, int startIndex) {
        short result = 0;
        for (int i = 0; i < 2; i++) {
            result <<= 8;
            result |= (b[i + startIndex] & 0xFF);
        }
        return result;
    }

    public static byte booleanToByte(Boolean b) {
        if (b == null) {
            return -1;
        } else if (b.equals(Boolean.TRUE)) {
            return 1;
        } else {
            return 0;
        }
    }

    public static Boolean byteToBoolean(byte b) {
        if (b == -1) {
            return null;
        } else if (b == 1) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }
}
