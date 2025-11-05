package top.turboweb.client.result;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.client.converter.Converter;

import java.util.Map;

/**
 * ClientResult 是 TurboWeb HTTP 客户端的响应结果封装类。
 * <p>
 * 提供对 HTTP 响应的类型安全访问和数据转换能力，
 * 支持将响应数据转换为指定对象或 Map。
 * <p>
 * 特性：
 * <ul>
 *     <li>封装 {@link HttpResponse}，提供状态码和响应头访问</li>
 *     <li>集成 {@link Converter}，支持自定义数据转换</li>
 *     <li>自动管理 Netty 缓冲区资源，防止内存泄漏</li>
 * </ul>
 * <p>
 * 使用注意：
 * <ul>
 *     <li>调用 {@link #as(Class)}、{@link #as(Object)} 或 {@link #as()} 后会自动释放底层响应资源</li>
 *     <li>如果需要多次访问响应数据，应先缓存转换后的对象</li>
 * </ul>
 */
public class ClientResult {

    private final HttpResponse response;
    private final Converter converter;

    /**
     * 构造方法
     *
     * @param response 原始 HTTP 响应
     * @param converter 数据转换器
     */
    public ClientResult(HttpResponse response, Converter converter) {
        this.response = response;
        this.converter = converter;
    }

    /**
     * 将响应体转换为指定类型的对象。
     * <p>
     * 调用完成后会自动释放底层 Netty 缓冲区。
     *
     * @param type 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的对象
     */
    public <T> T as(Class<T> type) {
        try {
            return converter.convert(response, type);
        } finally {
            release();
        }
    }

    /**
     * 将响应体转换为给定对象的类型。
     * <p>
     * 调用完成后会自动释放底层 Netty 缓冲区。
     *
     * @param object 示例对象，用于类型推断
     * @param <T> 数据类型
     * @return 转换后的对象
     */
    public <T> T as(T object) {
        try {
            return converter.convert(response, object);
        } finally {
            release();
        }
    }

    /**
     * 将响应体转换为通用 Map。
     * <p>
     * 调用完成后会自动释放底层 Netty 缓冲区。
     *
     * @return 转换后的 Map
     */
    public Map<?, ?> as() {
        try {
            return converter.convert(response, Map.class);
        } finally {
            release();
        }
    }

    /**
     * 获取响应头。
     *
     * @return {@link HttpHeaders} 对象
     */
    public HttpHeaders headers() {
        return response.headers();
    }

    /**
     * 获取 HTTP 响应状态码。
     *
     * @return HTTP 状态码
     */
    public int status() {
        return response.status().code();
    }

    /**
     * 释放底层响应资源，防止内存泄漏。
     * <p>
     * 对 {@link FullHttpResponse} 有效，仅在 refCnt() > 0 时释放。
     * 通常不需要手动调用，调用 {@link #as(Class)}、{@link #as(Object)} 或 {@link #as()} 会自动释放。
     */
    public void release() {
        if (response instanceof FullHttpResponse fullHttpResponse && fullHttpResponse.refCnt() > 0) {
            fullHttpResponse.release();
        }
    }
}
