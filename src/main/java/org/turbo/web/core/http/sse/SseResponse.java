package org.turbo.web.core.http.sse;

import io.netty.handler.codec.http.*;

import java.util.function.Consumer;

/**
 * sse的响应结果
 */
public class SseResponse extends DefaultHttpResponse {

    private final SseSession sseSession;
    private Consumer<SseSession> sseCallback;

    public SseResponse(HttpVersion version, HttpResponseStatus status, HttpHeaders headers, SseSession sseSession) {
        super(version, status, headers);
        assert sseSession != null;
        this.sseSession = sseSession;
        this.setSseHeaders();
    }

    private void setSseHeaders() {
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        this.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        HttpUtil.setTransferEncodingChunked(this, true); // 开启 Chunked 传输
    }

    public void setSseCallback(Consumer<SseSession> sseCallback) {
        this.sseCallback = sseCallback;
    }

    /**
     * 开始sse(框架会自行调用，请勿手动调用)
     */
    public void startSse() {
        if (sseCallback != null) {
            sseCallback.accept(sseSession);
        }
    }
}
