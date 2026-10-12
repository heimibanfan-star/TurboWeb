package top.turboweb.concurrent.limit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定周期令牌桶：初始满、耗尽、补币、并发不超发。
 */
class FixedIntervalTokenBucketTest {

    /** interval 取 3600s：本次运行内绝不可能补币，"耗尽"因此是确定的。 */
    private static final long NEVER = 3_600L;

    @Test
    void startsFullThenDrainsToEmpty() {
        FixedIntervalTokenBucket bucket = new FixedIntervalTokenBucket(2, NEVER);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "上限 2 用尽后必须拒绝");
    }

    @Test
    void zeroIntervalRefillsOnEveryCall() {
        FixedIntervalTokenBucket bucket = new FixedIntervalTokenBucket(1, 0L);
        for (int i = 0; i < 5; i++) {
            // 用 String 重载：for 循环变量不是 effectively final，喂给 Supplier<String> 编译不过
            assertTrue(bucket.tryAcquire(), "interval=0 时每次调用都应补币，第 " + i + " 次失败");
        }
    }

    @Test
    void largeIntervalDoesNotRefillWithinWindow() {
        FixedIntervalTokenBucket bucket = new FixedIntervalTokenBucket(1, NEVER);
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void concurrentAcquiresNeverExceedAvailableTokens() throws Exception {
        FixedIntervalTokenBucket bucket = new FixedIntervalTokenBucket(3, NEVER);
        // 先在主线程取一次：把 lastUpdateTime 从 0 推到 now，消掉"首枚调用都可能触发补币"的启动竞态
        assertTrue(bucket.tryAcquire());

        int threads = 8;
        int attemptsPerThread = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int a = 0; a < attemptsPerThread; a++) {
                        if (bucket.tryAcquire()) {
                            successes.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "TokenBucketProbe-" + i).start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "探测线程必须在 10s 内全部返回");
        assertEquals(2, successes.get(), "预取 1 枚后剩 2 枚，并发不得超发");
    }
}
