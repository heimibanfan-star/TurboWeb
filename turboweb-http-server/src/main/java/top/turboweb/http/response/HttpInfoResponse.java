package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.commons.config.GlobalConfig;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 信息响应对象。
 * <p>
 * 基于 {@link DefaultFullHttpResponse} 封装，提供便捷方法设置响应内容、响应类型和 Cookie。
 * 支持：
 * <ul>
 *     <li>直接写入字符串或字节数组作为响应内容</li>
 *     <li>设置响应内容类型（Content-Type）</li>
 *     <li>添加响应 Cookie（Set-Cookie）</li>
 * </ul>
 */
public class HttpInfoResponse extends DefaultFullHttpResponse {

    /**
     * 使用指定 HTTP 状态码构造响应对象，默认 HTTP/1.1 版本。
     *
     * @param status HTTP 响应状态码
     */
    public HttpInfoResponse(HttpResponseStatus status) {
        super(HttpVersion.HTTP_1_1, status);
    }

    /**
     * 使用指定 HTTP 状态码和内容构造响应对象，默认 HTTP/1.1 版本。
     *
     * @param status  HTTP 响应状态码
     * @param content 响应内容字节缓冲区
     */
    public HttpInfoResponse(HttpResponseStatus status, ByteBuf content) {
        super(HttpVersion.HTTP_1_1, status, content);
    }

    /**
     * 设置响应内容（字符串）。
     * <p>
     * 会根据指定字符集将字符串转换为字节数组，同时更新 Content-Length。
     *
     * @param content 响应内容
     * @param charset 字符集
     */
    public void setContent(String content, Charset charset) {
        // 将内容转按照utf-8转化为字节数组
        byte[] bytes = content.getBytes(charset);
        this.content().clear();
        this.content().writeBytes(bytes);
        this.headers().setInt("Content-Length", bytes.length);
    }

    /**
     * 设置响应内容（字节数组）。
     * <p>
     * 会清空原有内容并更新 Content-Length。
     *
     * @param content 响应内容字节数组
     */
    public void setContent(byte[] content) {
        this.content().clear();
        this.headers().setInt("Content-Length", content.length);
        this.content().writeBytes(content);
    }

    /**
     * 设置响应内容（字符串）。
     * <p>
     * 使用全局默认响应字符集（{@link GlobalConfig#getResponseCharset()}）。
     *
     * @param content 响应内容
     */
    public void setContent(String content) {
        this.setContent(content, GlobalConfig.getResponseCharset());
    }

    /**
     * 设置响应内容类型（Content-Type）。
     *
     * @param contentType 内容类型字符串，如 "text/html;charset=UTF-8"
     */
    public void setContentType(String contentType) {
        this.headers().set("Content-Type", contentType);
    }

    /**
     * 设置响应 Cookie。
     * <p>
     * 如果已有 Set-Cookie 头部，会追加新的 Cookie；否则创建新的 Set-Cookie。
     *
     * @param key   Cookie 名称
     * @param value Cookie 值
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
