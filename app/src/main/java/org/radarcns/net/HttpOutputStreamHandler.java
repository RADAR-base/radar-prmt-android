package org.radarcns.net;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by joris on 18/10/2016.
 */
public interface HttpOutputStreamHandler {
    /**
     * Write any output without closing the stream.
     */
    void handleOutput(OutputStream out) throws IOException;
}
