package org.turbo.core.http.response;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;

/**
 * http响应对象
 */
public class HttpInfoResponse extends DefaultFullHttpResponse {

    public HttpInfoResponse(HttpVersion version, HttpResponseStatus status) {
        super(version, status);
    }

    /**
     * 设置响应内容
     *
     * @param content 内容
     */
    public void setContent(String content) {
        // 将内容转按照utf-8转化为字节数组
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        this.content().writeBytes(bytes);
        this.headers().setInt("Content-Length", bytes.length);
    }

    /**
     * 设置响应内容类型
     *
     * @param contentType 内容类型
     */
    public void setContentType(String contentType) {
        this.headers().set("Content-Type", contentType);
    }
}
