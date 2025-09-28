package top.turboweb.commons.limit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/**
 * 固定周期的令牌桶
 */
public class FixedIntervalTokenBucket implements TokenBucket {

    private final AtomicInteger tokenCount;
    private volatile long lastUpdateTime = 0;
    private final StampedLock stampedLock = new StampedLock();
    private final int maxTokenCount;
    private final long intervalSeconds;

    /**
     * 构造函数
     * @param maxTokenCount 最大令牌数
     * @param intervalSeconds 令牌间隔
     */
    public FixedIntervalTokenBucket(int maxTokenCount, long intervalSeconds) {
        this.maxTokenCount = maxTokenCount;
        this.intervalSeconds = intervalSeconds;
        this.tokenCount = new AtomicInteger(maxTokenCount);
    }

    /**
     * 尝试获取令牌
     * @return 是否成功获取令牌
     */
    @Override
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        // 判断是否需要更新令牌
        if (Math.abs(now - lastUpdateTime) >= intervalSeconds * 1000) {
            long writeStamp = stampedLock.writeLock();
            try {
                // 防止令牌更新多次
                if (Math.abs(now - lastUpdateTime) >= intervalSeconds * 1000) {
                    lastUpdateTime = now;
                    tokenCount.set(maxTokenCount);
                }
            } finally {
                stampedLock.unlockWrite(writeStamp);
            }
        }
        // 判断是否有正在写入的线程
        long stamp = stampedLock.tryOptimisticRead();
        // 判断令牌桶是否发生了重置
        if (stampedLock.validate(stamp)) {
            return popToken();
        } else {
            stamp = stampedLock.readLock();
            try {
                return popToken();
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }
    }

    /**
     * 弹出令牌
     * @return 是否成功弹出令牌
     */
    private boolean popToken() {
        for (;;) {
            int tokens = tokenCount.get();
            if (tokens <= 0) {
                return false;
            }
            if (tokenCount.compareAndSet(tokens, tokens - 1)) {
                return true;
            }
        }
    }
}
