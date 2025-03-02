package org.turbo.web.core.http.sse;

import io.netty.handler.codec.http.HttpResponse;

/**
 * 封装sse的结果对象
 */
public class SseResultObject {
    // 构建的新的响应对象
    private final HttpResponse httpResponse;
    // sse的回话对象
    private final SSESession sseSession;

    public SseResultObject(HttpResponse httpResponse, SSESession sseSession) {
        this.httpResponse = httpResponse;
        this.sseSession = sseSession;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public SSESession getSseSession() {
        return sseSession;
    }

}
