package top.turboweb.rest.core;

import java.util.Objects;

/**
 * http请求行：存储请求方式与请求目标两个属性，对应请求行的方法字段与目标字段。
 *
 * @param method 请求方式，不能为null
 * @param target 请求目标，不能为null
 */
public record RequestLine(HttpRequestMethod method, HttpRequestTarget target) {

    /**
     * 创建请求行，校验两个属性均不为null。
     *
     * @throws NullPointerException method 或 target 为null
     */
    public RequestLine {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(target, "target must not be null");
    }
}
