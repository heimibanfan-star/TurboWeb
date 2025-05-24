package org.turboweb.core.http.response;

import io.netty.handler.codec.http.HttpResponse;
import org.turboweb.core.http.connect.ConnectSession;

/**
 * 封装sse的结果对象
 */
public class SseResultObject {
    // 构建的新的响应对象
    private final HttpResponse httpResponse;
    // sse的回话对象
    private final ConnectSession connectSession;

    public SseResultObject(HttpResponse httpResponse, ConnectSession connectSession) {
        this.httpResponse = httpResponse;
        this.connectSession = connectSession;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public ConnectSession getSseSession() {
        return connectSession;
    }

}
