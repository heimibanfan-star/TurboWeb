package org.turbo.web.core.http.response;

import io.netty.handler.codec.http.*;
import org.turbo.web.core.connect.ConnectSession;

import java.util.function.Consumer;

/**
 * sse的响应结果
 */
public class SseResponse extends DefaultHttpResponse {

    private final ConnectSession connectSession;
    private Consumer<ConnectSession> sseCallback;

    public SseResponse(HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ConnectSession connectSession) {
        super(version, status, headers);
        assert connectSession != null;
        this.connectSession = connectSession;
        this.setSseHeaders();
    }

    private void setSseHeaders() {
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        this.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        HttpUtil.setTransferEncodingChunked(this, true); // 开启 Chunked 传输
    }

    public void setSseCallback(Consumer<ConnectSession> sseCallback) {
        this.sseCallback = sseCallback;
    }

    /**
     * 开始sse(框架会自行调用，请勿手动调用)
     */
    public void startSse() {
        if (sseCallback != null) {
            sseCallback.accept(connectSession);
        }
    }
}
