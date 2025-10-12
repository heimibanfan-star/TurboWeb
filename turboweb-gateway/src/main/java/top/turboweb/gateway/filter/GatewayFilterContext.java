package top.turboweb.gateway.filter;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 网关过滤器上下文
 */
public interface GatewayFilterContext <R> {

    /**
     * 添加过滤器
     * @param filter 过滤器
     * @return 网关过滤器上下文
     */
    GatewayFilterContext<R> addFilter(GatewayFilter<R> filter);

    /**
     * 启动过滤器
     *
     * @param request        请求
     * @param responseHelper 响应助手
     * @param promise        过滤器执行结果
     */
    void startFilter(FullHttpRequest request, ResponseHelper responseHelper, ChannelPromise promise);
}
