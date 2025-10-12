package top.turboweb.gateway.filter;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 网关过滤器接口
 */
@FunctionalInterface
public interface GatewayFilter<R> {

    /**
     * 过滤器方法
     * @param request 请求对象
     * @param responseHelper 响应对象
     * @return 是否继续执行下一个过滤器
     */
    R filter(FullHttpRequest request, ResponseHelper responseHelper);
}
