package top.turboweb.client.interceptor;

import io.netty.handler.codec.http.HttpResponse;

/**
 * HTTP 响应拦截器接口。
 * <p>
 * 提供在请求响应返回后进行自定义处理的能力，例如：
 * <ul>
 *     <li>统一处理响应状态码或错误码</li>
 *     <li>日志记录或性能监控</li>
 *     <li>对响应体进行缓存或修改</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>{@code
 * client.addResponseInterceptor(response -> {
 *     if (response.status().code() >= 400) {
 *         // 自定义异常处理或日志
 *     }
 *     return response;
 * });
 * }</pre>
 * <p>
 * 注意：
 * <ul>
 *     <li>拦截器必须返回非 null 的 {@link HttpResponse} 对象，否则客户端会抛出 {@link top.turboweb.commons.exception.TurboHttpClientException}</li>
 *     <li>可以通过链式添加多个拦截器，执行顺序按添加顺序</li>
 * </ul>
 */
@FunctionalInterface
public interface ResponseInterceptor {

    /**
     * 拦截并处理 HTTP 响应。
     *
     * @param response 当前响应对象
     * @return 处理后的响应对象，不能为 null
     */
    HttpResponse intercept(HttpResponse response);
}
