package top.turboweb.gateway.breaker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
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
        volatile int status = STATUS_OPEN;
        int failCount = 0;
        int successCount = 0;
        long time = 0;
        final ReentrantLock lock = new ReentrantLock();
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
        int i = random.nextInt();
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
