package top.turboweb.loadbalance.breaker;

import java.util.Set;

/**
 * 断路器接口。
 *
 * <p>用于在负载均衡或网关调用中对服务的健康状态进行保护，
 * 根据请求成功/失败的历史统计，决定是否允许新的请求通过。
 * 实现类可以提供不同的策略（如基于超时、失败率、错误码等）。
 *
 * <p>典型用法：
 * <ul>
 *     <li>在请求失败时调用 {@link #setFail(String)}</li>
 *     <li>在请求成功时调用 {@link #setSuccess(String)}</li>
 *     <li>在发送请求前调用 {@link #isAllow(String)} 判断是否允许</li>
 * </ul>
 */
public interface Breaker {

    /**
     * 获取断路器的超时时间。
     *
     * <p>超时时间通常用于判断服务调用是否超过可接受的延迟阈值，
     * 超时则视为一次失败。
     *
     * @return 超时时间（毫秒）
     */
    long getTimeout();

    /**
     * 标记指定 URI 的请求失败。
     *
     * <p>实现类可根据失败次数、失败率或时间窗口更新断路器状态，
     * 可能导致后续请求被拒绝。
     *
     * @param uri 请求的目标 URI
     */
    void setFail(String uri);

    /**
     * 标记指定 URI 的请求成功。
     *
     * <p>成功调用可用于恢复断路器状态，使请求重新被允许通过。
     *
     * @param uri 请求的目标 URI
     */
    void setSuccess(String uri);

    /**
     * 判断指定 URI 是否允许请求通过。
     *
     * <p>根据断路器的内部状态（如失败次数、失败率、超时等）决定是否拒绝请求。
     *
     * @param uri 请求的目标 URI
     * @return {@code true} 表示允许请求通过，{@code false} 表示拒绝
     */
    boolean isAllow(String uri);

    /**
     * 获取被认为是失败的 HTTP 状态码集合。
     *
     * <p>调用者可根据此集合判断请求响应是否属于失败状态，
     * 进而触发断路器的失败统计。
     *
     * @return 失败状态码集合
     */
    Set<Integer> failStatusCode();
}
