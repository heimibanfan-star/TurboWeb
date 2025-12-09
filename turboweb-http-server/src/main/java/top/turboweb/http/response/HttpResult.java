package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JsonSerializer;

import java.util.Objects;

/**
 * HTTP 响应封装对象。
 * <p>
 * 根据响应内容类型自动设置 Content-Type：
 * <ul>
 *     <li>如果 data 为 {@link String}，则返回 "text/html;charset=utf-8"</li>
 *     <li>如果 data 为其他对象类型，则使用 {@link JsonSerializer} 序列化为 JSON，并返回 "application/json;charset=utf-8"</li>
 * </ul>
 * <p>
 * 提供构造方法和静态工厂方法快速创建不同状态码的响应对象。
 *
 * @param <T> 响应内容类型
 */
public class HttpResult <T> {

    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final T data;

    public HttpResult(int status) {
        this(status, null, null);
    }

    public HttpResult(T data) {
        this(200, data);
    }

    public HttpResult(int status, T data) {
        this(status, null, data);
    }

    public HttpResult(int status, HttpHeaders headers, T data) {
        this.status = HttpResponseStatus.valueOf(status);
        this.headers = Objects.requireNonNullElseGet(headers, DefaultHttpHeaders::new);
        this.data = data;
    }

    public HttpResponse createResponse(JsonSerializer jsonSerializer) {
        // 创建响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        // 设置响应头
        response.headers().set(headers);
        // 处理响应内容
        String content;
        if (data instanceof String s) {
            content = s;
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=utf-8");
        } else {
            // 序列化对象
            content = jsonSerializer.beanToJson(data);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=utf-8");
        }
        // 设置响应内容
        setContent(response, content);
        return response;
    }

    /**
     * 设置响应体内容
     *
     * @param response 响应对象
     * @param content 响应体内容
     */
    private void setContent(FullHttpResponse response, String content) {
        response.content().clear();
        if (content != null) {
            response.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        }
    }

    /**
     * 创建响应对象
     *
     * @param status 响应状态码
     * @param headers 响应头
     * @param data 响应内容
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> create(int status, HttpHeaders headers, T data) {
        return new HttpResult<>(status, headers, data);
    }

    /**
     * 创建响应对象
     *
     * @param status 响应状态码
     * @param data 响应内容
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> create(int status, T data) {
        return new HttpResult<>(status, data);
    }

    /**
     * 创建响应状态码为200的响应对象
     *
     * @param data 响应内容
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> ok(T data) {
        return new HttpResult<>(200, data);
    }

    /**
     * 创建响应状态码为200的响应对象
     *
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> ok() {
        return new HttpResult<>(200);
    }

    /**
     * 创建响应状态码为500的响应对象
     *
     * @param data 响应内容
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> err(T data) {
        return new HttpResult<>(500, data);
    }

    /**
     * 创建响应状态码为500的响应对象
     *
     * @param <T> 响应内容类型
     * @return 响应对象
     */
    public static <T> HttpResult<T> err() {
        return new HttpResult<>(500);
    }
}
