package top.turboweb.loadbalance.breaker;

import java.util.Set;

/**
 * 空实现断路器（No-op Breaker）。
 *
 * <p>该类实现了 {@link Breaker} 接口，但不会对请求进行任何限制或统计。
 * 所有请求均被允许通过，失败/成功状态不会被记录，适用于不需要断路器保护的场景。
 *
 * <p>主要特点：
 * <ul>
 *     <li>{@link #isAllow(String)} 永远返回 {@code true}</li>
 *     <li>{@link #setFail(String)} 和 {@link #setSuccess(String)} 不做任何操作</li>
 *     <li>{@link #getTimeout()} 返回 {@link Long#MAX_VALUE}</li>
 *     <li>{@link #failStatusCode()} 返回空集合</li>
 * </ul>
 *
 * <p>可作为默认断路器使用，或者在测试/开发环境中避免断路器逻辑干扰。
 */
public class EmptyBreaker implements Breaker {
    @Override
    public long getTimeout() {
        return Long.MAX_VALUE;
    }

    @Override
    public void setFail(String uri) {
    }

    @Override
    public void setSuccess(String uri) {
    }

    @Override
    public boolean isAllow(String uri) {
        return true;
    }


    @Override
    public Set<Integer> failStatusCode() {
        return Set.of();
    }
}
