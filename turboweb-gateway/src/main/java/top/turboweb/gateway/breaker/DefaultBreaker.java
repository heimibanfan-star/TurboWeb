package top.turboweb.gateway.breaker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 默认断路器
 */
public class DefaultBreaker implements Breaker {

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
        private final AtomicInteger status = new AtomicInteger(STATUS_OPEN);
        private final AtomicInteger failCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong time = new AtomicLong(0);
    }


    private final ConcurrentHashMap<String, HealthStatus> healthStatusMap = new ConcurrentHashMap<>();
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
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
        HealthStatus healthStatus = healthStatusMap.computeIfAbsent(uri, k -> new HealthStatus());
        trySwitchCloseToHalfOpen(healthStatus);
        for (; ; ) {
            // 读取变量
            int status = healthStatus.status.get();
            int successCount = healthStatus.successCount.get();
            int failCount = healthStatus.failCount.get();
            long lastTime = healthStatus.time.get();
            if (status == STATUS_CLOSE) {
                break;
            }
            if (failCount == failThreshold - 1 && status != STATUS_HALF_OPEN) {
                // CAS失败重新尝试
                if (!healthStatus.failCount.compareAndSet(failCount, failThreshold)) {
                    continue;
                }
                // CAS成功，尝试将开放转化为关闭
                if (healthStatus.status.compareAndSet(STATUS_OPEN, STATUS_CLOSE)) {
                    healthStatus.time.set(System.currentTimeMillis());
                }
                break;
            } else if (status != STATUS_HALF_OPEN && healthStatus.failCount.compareAndSet(failCount, failCount + 1)) {
                break;
            } else {
                if (!healthStatus.failCount.compareAndSet(failCount, failCount + 1)) {
                    continue;
                }
                // 处理半开状态
                long totalRequest = failCount + 1 + successCount;
                // 计算百分比
                double percent = (double) successCount / totalRequest;
                if (percent > recoverPercent) {
                    // 打开断路器
                    trySwitchHalfOpenToOpen(healthStatus);
                    break;
                } else if (lastTime + recoverWindowTTL < System.currentTimeMillis()) {
                    // 尝试关闭断路器
                    trySwitchHalfOpenToClose(healthStatus);
                    break;
                }
                break;
            }
        }
    }

    @Override
    public void setSuccess(String uri) {
        uri = standardUri(uri);
        HealthStatus healthStatus = healthStatusMap.get(uri);
        if (healthStatus == null) {
            return;
        }
        // 尝试状态切换
        trySwitchCloseToHalfOpen(healthStatus);
        if (healthStatus.status.get() != STATUS_HALF_OPEN) {
            return;
        }
        for (; ; ) {
            int status = healthStatus.status.get();
            int failCount = healthStatus.failCount.get();
            int successCount = healthStatus.successCount.get();
            long lastTime = healthStatus.time.get();
            if (status != STATUS_HALF_OPEN) {
                break;
            }
            if (!healthStatus.successCount.compareAndSet(successCount, successCount + 1)) {
                continue;
            }
            long totalCount = failCount + successCount + 1;
            // 计算百分比
            double percent = (double) (successCount + 1) / totalCount;
            if (percent > recoverPercent) {
                trySwitchHalfOpenToOpen(healthStatus);
                break;
            } else if (lastTime + recoverWindowTTL < System.currentTimeMillis()) {
                trySwitchHalfOpenToClose(healthStatus);
                break;
            }
            break;
        }
    }

    @Override
    public boolean isBreak(String uri) {
        uri = standardUri(uri);
        // 健康状态检测
        HealthStatus healthStatus = healthStatusMap.get(uri);
        if (healthStatus == null) {
            return false;
        }
        trySwitchCloseToHalfOpen(healthStatus);
        // 判断断路器的状态
        if (healthStatus.status.get() == STATUS_OPEN) {
            return false;
        }
        if (healthStatus.status.get() == STATUS_HALF_OPEN) {
            return randomAllow();
        }
        return true;
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
        int i = random.nextInt();
        return i % 2 == 0;
    }

    /**
     * 尝试将关闭状态切换为半开放状态
     * @param healthStatus 健康状态
     */
    private void trySwitchCloseToHalfOpen(HealthStatus healthStatus) {
        if (healthStatus.status.get() != STATUS_CLOSE) {
            return;
        }
        if (healthStatus.time.get() + failWindowTTL < System.currentTimeMillis() && healthStatus.status.compareAndSet(STATUS_CLOSE, STATUS_HALF_OPEN)) {
            healthStatus.time.set(System.currentTimeMillis());
            healthStatus.failCount.set(0);
            healthStatus.successCount.set(0);
        }
    }

    /**
     * 尝试将半开放状态切换为打开状态
     * @param healthStatus 健康状态
     */
    private void trySwitchHalfOpenToOpen(HealthStatus healthStatus) {
        if (healthStatus.status.get() != STATUS_HALF_OPEN) {
            return;
        }
        if (healthStatus.status.compareAndSet(STATUS_HALF_OPEN, STATUS_OPEN)) {
            healthStatus.time.set(System.currentTimeMillis());
            healthStatus.failCount.set(0);
            healthStatus.successCount.set(0);
        }
    }

    private void trySwitchHalfOpenToClose(HealthStatus healthStatus) {
        if (healthStatus.status.get() != STATUS_HALF_OPEN) {
            return;
        }
        if (healthStatus.status.compareAndSet(STATUS_HALF_OPEN, STATUS_CLOSE)) {
            healthStatus.time.set(System.currentTimeMillis());
            healthStatus.failCount.set(0);
            healthStatus.successCount.set(0);
        }
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
