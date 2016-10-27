package org.radarcns.net;

import java.util.List;
import java.util.Map;

public class HttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String content;

    public HttpResponse(int statusCode, Map<String, List<String>> headers, String content) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
