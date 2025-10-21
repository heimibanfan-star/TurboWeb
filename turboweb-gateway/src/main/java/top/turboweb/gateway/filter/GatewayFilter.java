package top.turboweb.gateway.filter;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 网关过滤器接口。
 * <p>
 * 每个 {@code GatewayFilter} 表示一个可插拔的请求处理单元，
 * 可在请求进入后或转发前执行自定义逻辑（例如鉴权、限流、日志、重写、缓存校验等）。
 * <p>
 * 过滤器通过返回值控制过滤链的执行：
 * <ul>
 *     <li>返回 {@code true}（或对应异步类型的 true）：继续执行下一个过滤器。</li>
 *     <li>返回 {@code false}：终止执行链，直接结束请求。</li>
 * </ul>
 *
 * @param <R> 返回类型，通常为 {@code Boolean} 或 {@code Mono<Boolean>}，
 *            分别对应同步与异步过滤链。
 */
@FunctionalInterface
public interface GatewayFilter<R> {

    /**
     * 执行过滤逻辑。
     * <p>
     * 该方法应包含过滤器的核心处理逻辑，例如：
     * <ul>
     *     <li>验证请求头、签名或 Token。</li>
     *     <li>记录请求日志或统计信息。</li>
     *     <li>修改请求（如添加自定义 Header）。</li>
     * </ul>
     *
     * @param request         当前 HTTP 请求对象（不可修改其底层缓冲区）。
     * @param responseHelper  响应辅助类，可用于快速生成响应或提前终止请求。
     * @return 控制过滤链执行的结果：
     *         <ul>
     *             <li>同步链：返回 {@code Boolean}</li>
     *             <li>异步链：返回 {@code Mono<Boolean>}</li>
     *         </ul>
     */
    R filter(FullHttpRequest request, ResponseHelper responseHelper);
}
