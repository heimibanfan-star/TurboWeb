package top.turboweb.gateway.filter;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 网关过滤器上下文。
 * <p>
 * 用于管理和调度一组 {@link GatewayFilter}，实现请求的链式过滤与控制。
 * <p>
 * 每个上下文可表示不同的执行模型：
 * <ul>
 *     <li>同步过滤链：{@code GatewayFilter<Boolean>}</li>
 *     <li>异步过滤链：{@code GatewayFilter<Mono<Boolean>>}</li>
 * </ul>
 * <p>
 * 框架通过该上下文统一启动过滤流程，并使用 {@link ChannelPromise}
 * 通知 Netty 异步执行的完成状态（成功或失败）。
 *
 * @param <R> 过滤器返回类型，通常为 {@code Boolean} 或 {@code Mono<Boolean>}
 */
public interface GatewayFilterContext <R> {

    /**
     * 向当前上下文中添加一个过滤器。
     * <p>
     * 过滤器将按添加顺序执行，重复添加同一实例可能被实现拒绝。
     *
     * @param filter 要添加的过滤器实例，不能为空
     * @return 当前 {@code GatewayFilterContext}，支持链式调用
     */
    GatewayFilterContext<R> addFilter(GatewayFilter<R> filter);

    /**
     * 启动过滤器链的执行。
     * <p>
     * 框架会按顺序执行所有过滤器，每个过滤器返回的结果用于控制执行流：
     * <ul>
     *     <li>返回 {@code true}：继续执行下一个过滤器。</li>
     *     <li>返回 {@code false}：中断执行链并立即结束请求。</li>
     * </ul>
     * <p>
     * 过滤执行完成后，将通过 {@link ChannelPromise} 通知 Netty：
     * <ul>
     *     <li>执行成功：调用 {@code promise.setSuccess()}。</li>
     *     <li>执行异常：调用 {@code promise.setFailure(Throwable)}。</li>
     * </ul>
     *
     * @param request         当前 HTTP 请求
     * @param responseHelper  响应工具，用于构造或提前返回响应
     * @param promise         Netty 的异步结果对象，用于标识过滤过程完成状态
     */
    void startFilter(FullHttpRequest request, ResponseHelper responseHelper, ChannelPromise promise);
}
