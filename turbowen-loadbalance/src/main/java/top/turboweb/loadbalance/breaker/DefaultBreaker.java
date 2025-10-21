package top.turboweb.loadbalance.breaker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 默认断路器实现。
 *
 * <p>该类基于经典的熔断器设计，维护每个请求 URI 的健康状态，
 * 根据失败次数、恢复时间、半开比例等策略控制请求是否允许通过。
 *
 * <p>状态机包含三种状态：
 * <ul>
 *     <li>{@code OPEN}：正常状态，允许请求通过</li>
 *     <li>{@code CLOSE}：熔断状态，拒绝请求，直到恢复时间到达</li>
 *     <li>{@code HALF_OPEN}：半开状态，允许部分请求尝试，通过成功率判断是否恢复为 OPEN</li>
 * </ul>
 *
 * <p>支持的自定义配置：
 * <ul>
 *     <li>failStatusCode：哪些 HTTP 状态码被视为失败</li>
 *     <li>failWindowTTL：失败检测窗口时间</li>
 *     <li>failThreshold：失败次数阈值，超过后切换到 CLOSE</li>
 *     <li>recoverTime：熔断恢复时间</li>
 *     <li>recoverWindowTTL：半开状态检测窗口</li>
 *     <li>recoverPercent：半开状态恢复成功率阈值</li>
 * </ul>
 */
public class DefaultBreaker implements Breaker {

    /**
     * 构造默认断路器并指定超时时间
     * @param timeout 超时时间（毫秒）
     */
    public DefaultBreaker(long timeout) {
        this.timeout = timeout;
    }

    public DefaultBreaker() {
        this(Long.MAX_VALUE);
    }

    private static final int STATUS_CLOSE = 0;
    private static final int STATUS_OPEN = 1;
    private static final int STATUS_HALF_OPEN = 2;

    private static class HealthStatus {
        volatile int status = STATUS_OPEN;
        int failCount = 0;
        int successCount = 0;
        long time = 0;
        final ReentrantLock lock = new ReentrantLock();
    }


    private final ConcurrentHashMap<String, HealthStatus> healthStatusMap = new ConcurrentHashMap<>();
    // 被判断为失败的状态码
    private Set<Integer> failStatusCode;
    private final long timeout;
    // 检测失败的事件窗口
    private long failWindowTTL;
    // 失败的阈值
    private int failThreshold;
    // 失败恢复时间
    private long recoverTime;
    // 恢复时间窗口
    private long recoverWindowTTL;
    // 恢复比例
    private double recoverPercent;

    public void setFailStatusCode(Set<Integer> failStatusCode) {
        failStatusCode.forEach(code -> Objects.requireNonNull(code, "status cannot be null"));
        // 转化为不可变集合
        this.failStatusCode = Collections.unmodifiableSet(failStatusCode);
    }

    public void setFailStatusCode(int... failStatusCode) {
        Set<Integer> codes = Arrays.stream(failStatusCode).boxed().collect(Collectors.toSet());
        setFailStatusCode(codes);
    }

    public void setFailWindowTTL(long failWindowTTL) {
        if (failWindowTTL <= 0) {
            throw new IllegalArgumentException("failWindowTTL must be greater than 0");
        }
        this.failWindowTTL = failWindowTTL;
    }

    public void setFailThreshold(int failThreshold) {
        if (failThreshold <= 0) {
            throw new IllegalArgumentException("failThreshold must be greater than 0");
        }
        this.failThreshold = failThreshold;
    }

    public void setRecoverTime(long recoverTime) {
        if (recoverTime <= 0) {
            throw new IllegalArgumentException("recoverTime must be greater than 0");
        }
        this.recoverTime = recoverTime;
    }

    public void setRecoverWindowTTL(long recoverWindowTTL) {
        if (recoverWindowTTL <= 0) {
            throw new IllegalArgumentException("recoverWindowTTL must be greater than 0");
        }
        this.recoverWindowTTL = recoverWindowTTL;
    }

    public void setRecoverPercent(double recoverPercent) {
        if (recoverPercent < 0 || recoverPercent > 1) {
            throw new IllegalArgumentException("recoverPercent must be greater than 0 and less than 1");
        }
        this.recoverPercent = recoverPercent;
    }



    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setFail(String uri) {
        uri = standardUri(uri);
        HealthStatus healthStatus = healthStatusMap.computeIfAbsent(uri, k -> {
            HealthStatus status = new HealthStatus();
            status.time = System.currentTimeMillis();
            return status;
        });
        healthStatus.lock.lock();
        try {
            testCloseToHalfOpen(healthStatus);
            // 判断是否在当前检测窗口
            if (healthStatus.status == STATUS_OPEN && System.currentTimeMillis() - healthStatus.time > failWindowTTL) {
                // 重置时间窗口
                healthStatus.time = System.currentTimeMillis();
                return;
            }
            // 阈值判断
            if (healthStatus.status == STATUS_OPEN && ++healthStatus.failCount >= failThreshold) {
                // 切换状态
                healthStatus.status = STATUS_CLOSE;
                return;
            }
            // 处理半开状态
            if (healthStatus.status == STATUS_HALF_OPEN) {
                handleForHalfOpen(healthStatus, false);
            }
        } finally {
            healthStatus.lock.unlock();
        }
    }


    @Override
    public void setSuccess(String uri) {
        uri = standardUri(uri);
        HealthStatus healthStatus = healthStatusMap.get(uri);
        if (healthStatus == null) {
            return;
        }
        // 不是半开状态忽略
        if (healthStatus.status == STATUS_OPEN) {
            return;
        }
        healthStatus.lock.lock();
        try {
            testCloseToHalfOpen(healthStatus);
            // 跳过非半开状态
            if (healthStatus.status != STATUS_HALF_OPEN) {
                return;
            }
            // 处理半开状态
            handleForHalfOpen(healthStatus, true);
        } finally {
            healthStatus.lock.unlock();
        }
    }

    /**
     * 半开状态处理逻辑，根据成功率判断是否恢复 OPEN
     */
    private void handleForHalfOpen(HealthStatus healthStatus, boolean isSuccess) {
        if (isSuccess) {
            healthStatus.successCount++;
        } else {
            healthStatus.failCount++;
        }
        double percent = healthStatus.successCount / (double) (healthStatus.successCount + healthStatus.failCount);
        // 判断是否到达阈值
        if (percent >= recoverPercent) {
            // 修改为打开状态
            healthStatus.status = STATUS_OPEN;
        } else if (healthStatus.time + recoverWindowTTL < System.currentTimeMillis()) {
            // 恢复失败，继续设置为关闭状态
            healthStatus.status = STATUS_CLOSE;
        }
    }

    /**
     * 检查 CLOSE 状态是否达到恢复时间，切换到 HALF_OPEN
     */
    private void testCloseToHalfOpen(HealthStatus healthStatus) {
        if (healthStatus.status != STATUS_CLOSE) {
            return;
        }
        // 判断是否达到恢复时间
        if (healthStatus.time + recoverTime < System.currentTimeMillis()) {
            healthStatus.status = STATUS_HALF_OPEN;
            healthStatus.time = System.currentTimeMillis();
            healthStatus.failCount = 0;
            healthStatus.successCount = 0;
        }
    }

    @Override
    public boolean isAllow(String uri) {
        uri = standardUri(uri);
        // 获取健康状态
        HealthStatus healthStatus = healthStatusMap.get(uri);
        if (healthStatus == null) {
            return true;
        }
        // 判断状态
        if (healthStatus.status == STATUS_OPEN) {
            return true;
        }
        if (healthStatus.status == STATUS_CLOSE) {
            // 判断是否到达恢复时间
            if (healthStatus.time + recoverTime < System.currentTimeMillis()) {
                healthStatus.lock.lock();
                try {
                    testCloseToHalfOpen(healthStatus);
                } finally {
                    healthStatus.lock.unlock();
                }
            } else {
                return false;
            }
        }
        return randomAllow();
    }

    @Override
    public Set<Integer> failStatusCode() {
        return failStatusCode;
    }

    /**
     * 随机判断是否允许通过
     * @return true表示允许通过
     */
    private boolean randomAllow() {
        int i = ThreadLocalRandom.current().nextInt();
        return i % 2 == 0;
    }

    /**
     * 标准化uri
     * @param uri 请求地址
     * @return 标准化后的uri
     */
    private String standardUri(String uri) {
        if (uri.indexOf('?') > 0) {
            uri = uri.substring(0, uri.indexOf('?'));
        }
        if (uri.endsWith("/")) {
            uri = uri.replaceAll("/$", "");
        }
        return uri;
    }
}
