package top.turboweb.client;

import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * rest 风格的 http 客户端门面：在 {@link HttpClientEngine} 之上提供
 * {@code get}、{@code post}、{@code put}、{@code delete}、{@code patch}
 * 五族便捷入口与通用 {@link #request(String, HttpRequestMethod, Object)} 入口，
 * 每族均提供无参与携带请求体两个重载——请求体不按方式裁剪，
 * GET、DELETE 等方式同样允许携带，是否接受由服务端裁决。
 *
 * <p>请求体的编码归本类：{@code null} 表示无请求体；{@code byte[]} 按原文发送
 * 且不改写 Content-Type；其余对象一律 JSON 序列化并以 UTF-8 编码发送，
 * 自动补 Content-Type 为 application/json。响应直接透出引擎聚合的
 * {@link ByteResponseMessage}，状态码裁决与响应体解码归调用方。</p>
 *
 * <p>携带 baseUrl 时请求路径与 baseUrl 拼接为完整目标（baseUrl 去尾部斜杠、
 * 路径补前导斜杠）；未携带时路径须为绝对地址，由引擎校验。本类自身无可变状态，
 * 可多线程共用；传输资源随引擎生命周期，由引擎侧 {@code close()} 释放。</p>
 */
public class RestHttpClient {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpClientEngine engine;
    private final String baseUrl;
    private final JsonSerializer jsonSerializer;

    /**
     * 建立客户端，无 baseUrl，使用默认 JSON 序列化器。
     *
     * @param engine 传输引擎，不能为 {@code null}
     * @throws NullPointerException 当 {@code engine} 为 {@code null} 时抛出
     */
    public RestHttpClient(HttpClientEngine engine) {
        this(engine, null);
    }

    /**
     * 建立客户端，使用默认 JSON 序列化器。
     *
     * @param engine  传输引擎，不能为 {@code null}
     * @param baseUrl 基础地址；为 {@code null} 或空串表示不拼接，
     *                非空时去尾部斜杠后与请求路径拼接
     * @throws NullPointerException 当 {@code engine} 为 {@code null} 时抛出
     */
    public RestHttpClient(HttpClientEngine engine, String baseUrl) {
        this(engine, baseUrl, new JacksonJsonSerializer());
    }

    /**
     * 建立客户端。
     *
     * @param engine         传输引擎，不能为 {@code null}
     * @param baseUrl        基础地址；为 {@code null} 或空串表示不拼接，
     *                       非空时去尾部斜杠后与请求路径拼接
     * @param jsonSerializer JSON 序列化器，不能为 {@code null}
     * @throws NullPointerException 当 {@code engine} 或 {@code jsonSerializer} 为 {@code null} 时抛出
     */
    public RestHttpClient(HttpClientEngine engine, String baseUrl, JsonSerializer jsonSerializer) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "jsonSerializer must not be null");
    }

    /**
     * 发起 GET 请求，无请求体。
     *
     * @param path 请求路径
     * @return 响应消息
     */
    public ByteResponseMessage get(String path) {
        return request(path, HttpRequestMethod.GET, null);
    }

    /**
     * 发起 GET 请求，携带请求体。
     *
     * @param path 请求路径
     * @param data 请求体数据，编码规则见类说明
     * @return 响应消息
     */
    public ByteResponseMessage get(String path, Object data) {
        return request(path, HttpRequestMethod.GET, data);
    }

    /**
     * 发起 POST 请求，无请求体。
     *
     * @param path 请求路径
     * @return 响应消息
     */
    public ByteResponseMessage post(String path) {
        return request(path, HttpRequestMethod.POST, null);
    }

    /**
     * 发起 POST 请求，携带请求体。
     *
     * @param path 请求路径
     * @param data 请求体数据，编码规则见类说明
     * @return 响应消息
     */
    public ByteResponseMessage post(String path, Object data) {
        return request(path, HttpRequestMethod.POST, data);
    }

    /**
     * 发起 PUT 请求，无请求体。
     *
     * @param path 请求路径
     * @return 响应消息
     */
    public ByteResponseMessage put(String path) {
        return request(path, HttpRequestMethod.PUT, null);
    }

    /**
     * 发起 PUT 请求，携带请求体。
     *
     * @param path 请求路径
     * @param data 请求体数据，编码规则见类说明
     * @return 响应消息
     */
    public ByteResponseMessage put(String path, Object data) {
        return request(path, HttpRequestMethod.PUT, data);
    }

    /**
     * 发起 DELETE 请求，无请求体。
     *
     * @param path 请求路径
     * @return 响应消息
     */
    public ByteResponseMessage delete(String path) {
        return request(path, HttpRequestMethod.DELETE, null);
    }

    /**
     * 发起 DELETE 请求，携带请求体。
     *
     * @param path 请求路径
     * @param data 请求体数据，编码规则见类说明
     * @return 响应消息
     */
    public ByteResponseMessage delete(String path, Object data) {
        return request(path, HttpRequestMethod.DELETE, data);
    }

    /**
     * 发起 PATCH 请求，无请求体。
     *
     * @param path 请求路径
     * @return 响应消息
     */
    public ByteResponseMessage patch(String path) {
        return request(path, HttpRequestMethod.PATCH, null);
    }

    /**
     * 发起 PATCH 请求，携带请求体。
     *
     * @param path 请求路径
     * @param data 请求体数据，编码规则见类说明
     * @return 响应消息
     */
    public ByteResponseMessage patch(String path, Object data) {
        return request(path, HttpRequestMethod.PATCH, data);
    }

    /**
     * 发起请求，无请求体。
     *
     * @param path   请求路径
     * @param method 请求方式
     * @return 响应消息
     */
    public ByteResponseMessage request(String path, HttpRequestMethod method) {
        return request(path, method, null);
    }

    /**
     * 通用请求入口：按请求方式发起请求，请求体编码规则见类说明。
     *
     * @param path   请求路径
     * @param method 请求方式，不能为 {@code null}
     * @param data   请求体数据；{@code null} 表示无请求体
     * @return 响应消息，状态码裁决与响应体解码归调用方
     * @throws NullPointerException 当 {@code path} 或 {@code method} 为 {@code null} 时抛出
     */
    public ByteResponseMessage request(String path, HttpRequestMethod method, Object data) {
        return send(method, resolveUrl(path), data);
    }

    /**
     * 拼接请求目标：无 baseUrl 时路径原样返回（须为绝对地址，由引擎校验）；
     * 有 baseUrl 时按前导斜杠补齐拼接。
     */
    private String resolveUrl(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (baseUrl.isEmpty()) {
            return path;
        }
        return path.charAt(0) == '/' ? baseUrl + path : baseUrl + '/' + path;
    }

    /**
     * 编码请求体并交由引擎发送：{@code null} 无体；{@code byte[]} 原样；
     * 其余 JSON 序列化并自动补 Content-Type。
     */
    private ByteResponseMessage send(HttpRequestMethod method, String url, Object data) {
        byte[] body;
        if (data == null) {
            body = EMPTY_BODY;
        } else if (data instanceof byte[] bytes) {
            body = bytes;
        } else {
            body = jsonSerializer.beanToJson(data).getBytes(StandardCharsets.UTF_8);
        }
        HttpRequestMessage<byte[]> message =
                new HttpRequestMessage<>(new RequestLine(method, new RequestTarget(url)), body);
        if (data != null && !(data instanceof byte[])) {
            message.headers().set(CONTENT_TYPE, APPLICATION_JSON);
        }
        return engine.request(message);
    }

    /**
     * 归一 baseUrl：{@code null} 或空串按无 baseUrl 处理，非空去全部尾部斜杠。
     */
    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        return baseUrl.substring(0, end);
    }
}
