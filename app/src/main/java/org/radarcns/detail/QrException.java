package org.radarcns.detail;

public class QrException extends IllegalArgumentException {
    public QrException(String message) {
        super(message);
    }

    public QrException(String message, Throwable ex) {
        super(message, ex);
    }
}
