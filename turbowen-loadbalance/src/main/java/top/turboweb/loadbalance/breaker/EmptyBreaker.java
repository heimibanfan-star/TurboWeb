package top.turboweb.loadbalance.breaker;

import java.util.Set;

/**
 * 空实现断路器
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
