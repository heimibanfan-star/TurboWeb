package top.turboweb.client.interceptor;

import io.netty.handler.codec.http.HttpResponse;

/**
 * 响应拦截器
 */
@FunctionalInterface
public interface ResponseInterceptor {

    HttpResponse intercept(HttpResponse response);
}
