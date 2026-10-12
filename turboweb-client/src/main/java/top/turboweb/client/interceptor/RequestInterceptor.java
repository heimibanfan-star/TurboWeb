package top.turboweb.client.interceptor;

import io.netty.handler.codec.http.HttpRequest;


/**
 * HTTP 请求拦截器接口。
 * <p>
 * 提供在请求发送前进行自定义处理的能力，例如：
 * <ul>
 *     <li>添加或修改请求头</li>
 *     <li>签名或鉴权</li>
 *     <li>日志记录</li>
 *     <li>修改请求 URI 或方法</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>{@code
 * client.addRequestInterceptor(request -> {
 *     request.headers().set("Authorization", "Bearer token");
 *     return request;
 * });
 * }</pre>
 * <p>
 * 注意：
 * <ul>
 *     <li>拦截器必须返回非 null 的 {@link HttpRequest} 对象，否则客户端会抛出 {@link top.turboweb.commons.exception.TurboHttpClientException}</li>
 *     <li>可以通过链式添加多个拦截器，执行顺序按添加顺序</li>
 * </ul>
 */
@FunctionalInterface
public interface RequestInterceptor {

    /**
     * 拦截并处理 HTTP 请求。
     *
     * @param request 当前请求对象
     * @return 处理后的请求对象，不能为 null
     */
    HttpRequest intercept(HttpRequest request);
}
