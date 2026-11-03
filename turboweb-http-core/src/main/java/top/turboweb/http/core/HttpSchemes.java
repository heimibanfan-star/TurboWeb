package top.turboweb.http.core;

import java.util.Objects;

/**
 * http 协议 scheme 值对象：以预置的 {@link #Http} 与 {@link #Https} 两个不可变实例
 * 限定 http 与 https 两种取值，类外不能新建实例。
 *
 * <p>取值经 {@link #of(String)} 按字符串解析：仅接受小写 http 与 https，
 * 命中即返回对应预置实例，否则抛出异常。</p>
 */
public final class HttpSchemes {

    /** http 协议实例，取值 http。 */
    public static final HttpSchemes Http = new HttpSchemes("http");

    /** https 协议实例，取值 https。 */
    public static final HttpSchemes Https = new HttpSchemes("https");

    /** 协议取值。 */
    private final String value;

    /**
     * 以协议取值构造实例。类外不开放，实例仅由预置常量持有。
     *
     * @param value 协议取值
     */
    private HttpSchemes(String value) {
        this.value = value;
    }

    /**
     * 按字符串解析协议值。
     *
     * @param value 协议取值，仅小写 http 与 https 被接受
     * @return 对应协议实例
     * @throws NullPointerException     当 {@code value} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 {@code value} 非 http 或 https 时抛出
     */
    public static HttpSchemes of(String value) {
        Objects.requireNonNull(value, "scheme must not be null");
        if (Http.value.equals(value)) {
            return Http;
        }
        if (Https.value.equals(value)) {
            return Https;
        }
        throw new IllegalArgumentException("不支持的 scheme: " + value);
    }

    /**
     * 获取协议取值字符串。
     *
     * @return 协议取值，为 http 或 https
     */
    public String value() {
        return value;
    }
}