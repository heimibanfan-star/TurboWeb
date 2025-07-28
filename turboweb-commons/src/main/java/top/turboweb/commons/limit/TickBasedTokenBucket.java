package top.turboweb.commons.limit;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 节拍式的令牌桶
 */
public class TickBasedTokenBucket implements TokenBucket, Closeable {

    private final int maxTokenCount;
    private final long intervalNanos;
    private final AtomicInteger tokenCount = new AtomicInteger(0);
    private final AtomicBoolean isClose = new AtomicBoolean(false);

    public TickBasedTokenBucket(int maxTokenCount, Duration interval) {
        this.maxTokenCount = maxTokenCount;
        this.intervalNanos = interval.toNanos();
        startGenerator();
    }

    /**
     * 启动填充令牌线程
     */
    private void startGenerator() {
        Thread.ofVirtual().start(() -> {
            for (;;) {
                try {
                    if (isClose.get()) {
                        break;
                    }
                    // 停顿
                    LockSupport.parkNanos(intervalNanos);
                    // 填充令牌
                    for (; ; ) {
                        int tc = tokenCount.get();
                        if (tc >= maxTokenCount) {
                            break;
                        }
                        if (tokenCount.compareAndSet(tc, tc + 1)) {
                            break;
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        }).start();
    }

    @Override
    public boolean tryAcquire() {
        for (; ; ) {
            int tc = tokenCount.get();
            if (tc <= 0) {
                return false;
            }
            if (tokenCount.compareAndSet(tc, tc - 1)) {
                return true;
            }
        }
    }

    @Override
    public void close() throws IOException {
        isClose.compareAndSet(false, true);
    }
}
