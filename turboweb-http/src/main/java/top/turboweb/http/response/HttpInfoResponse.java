package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * http响应对象
 */
public class HttpInfoResponse extends DefaultFullHttpResponse {

    public HttpInfoResponse(HttpResponseStatus status) {
        super(HttpVersion.HTTP_1_1, status);
    }

    public HttpInfoResponse(HttpResponseStatus status, ByteBuf content) {
        super(HttpVersion.HTTP_1_1, status, content);
    }

    /**
     * 设置响应内容
     *
     * @param content 内容
     */
    public void setContent(String content, Charset charset) {
        // 将内容转按照utf-8转化为字节数组
        byte[] bytes = content.getBytes(charset);
        this.content().clear();
        this.content().writeBytes(bytes);
        this.headers().setInt("Content-Length", bytes.length);
    }

    public void setContent(byte[] content) {
        this.content().clear();
        this.headers().setInt("Content-Length", content.length);
        this.content().writeBytes(content);
    }

    public void setContent(String content) {
        this.setContent(content, StandardCharsets.UTF_8);
    }

    /**
     * 设置响应内容类型
     *
     * @param contentType 内容类型
     */
    public void setContentType(String contentType) {
        this.headers().set("Content-Type", contentType);
    }

    /**
     * 设置响应cookie
     *
     * @param key   键
     * @param value 值
     */
    public void setCookie(String key, String value) {
        if (this.headers().contains(HttpHeaderNames.SET_COOKIE)) {
            // 如果存在，则添加
            this.headers().add(HttpHeaderNames.SET_COOKIE, key + "=" + value);
        } else {
            // 如果不存在，则设置
            this.headers().set(HttpHeaderNames.SET_COOKIE, key + "=" + value);
        }
    }
}
