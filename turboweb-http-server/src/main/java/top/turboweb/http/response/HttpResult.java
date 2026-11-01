package top.turboweb.http.response;

import top.turboweb.http.core.Headers;
import top.turboweb.http.core.StatusLine;

import java.util.Objects;

/**
 * HTTP 响应值对象：承载状态行、响应头与原始业务数据，供结果转化环节装配为响应消息。
 * <p>
 * 本类只做持有，不承载内容编码与实体头的装配：数据按入参原样持有，
 * 序列化与 Content-Type、Content-Length 的补齐由结果转化环节按实际内容完成。
 * 头部为消息自有的 {@link Headers} 实体，读写直接作用于本对象。
 * 提供构造方法和静态工厂方法快速创建不同状态码的响应值对象。
 * </p>
 */
public final class HttpResult {

    /** 响应状态行，取自框架状态行注册表 */
    private final StatusLine statusLine;
    /** 响应头实体，构造期一次建立或按入参原样持有 */
    private final Headers headers;
    /** 原始业务数据，可为 null */
    private final Object data;

    public HttpResult(int status) {
        this(status, null, null);
    }

    public HttpResult(Object data) {
        this(200, data);
    }

    public HttpResult(int status, Object data) {
        this(status, null, data);
    }

    public HttpResult(int status, Headers headers, Object data) {
        this.statusLine = StatusLine.of(status);
        this.headers = Objects.requireNonNullElseGet(headers, Headers::new);
        this.data = data;
    }

    /**
     * 获取响应状态行。
     *
     * @return 状态行，已收录状态码为注册表共享实例
     */
    public StatusLine statusLine() {
        return statusLine;
    }

    /**
     * 获取响应头实体。
     *
     * @return 响应头实体，重复访问返回同一实例
     */
    public Headers headers() {
        return headers;
    }

    /**
     * 获取原始业务数据。
     *
     * @return 业务数据，未携带时为 null
     */
    public Object data() {
        return data;
    }

    /**
     * 创建响应值对象
     *
     * @param status  响应状态码
     * @param headers 响应头，为 null 时自建空头部实体
     * @param data    响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult create(int status, Headers headers, Object data) {
        return new HttpResult(status, headers, data);
    }

    /**
     * 创建响应值对象
     *
     * @param status 响应状态码
     * @param data   响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult create(int status, Object data) {
        return new HttpResult(status, data);
    }

    /**
     * 创建响应状态码为200的响应值对象
     *
     * @param data 响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult ok(Object data) {
        return new HttpResult(200, data);
    }

    /**
     * 创建响应状态码为200的响应值对象
     *
     * @return 响应值对象
     */
    public static HttpResult ok() {
        return new HttpResult(200);
    }

    /**
     * 创建响应状态码为500的响应值对象
     *
     * @param data 响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult err(Object data) {
        return new HttpResult(500, data);
    }

    /**
     * 创建响应状态码为500的响应值对象
     *
     * @return 响应值对象
     */
    public static HttpResult err() {
        return new HttpResult(500);
    }
}
