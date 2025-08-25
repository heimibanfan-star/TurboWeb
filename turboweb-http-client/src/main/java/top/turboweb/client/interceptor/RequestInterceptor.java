package top.turboweb.client.interceptor;

import io.netty.handler.codec.http.HttpRequest;

/**
 * 请求拦截器
 */
@FunctionalInterface
public interface RequestInterceptor {

    HttpRequest intercept(HttpRequest request);
}
