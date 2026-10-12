package top.turboweb.concurrent.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分段锁的分段语义与 null 键退化。
 *
 * <p>互斥必须在<b>另一个线程</b>上取证：{@code ReentrantLock} 对同线程重入必然返回 true，
 * 在主线程里 tryLock 自己已持有的键永远真，证不出互斥。
 */
class SegmentLockTest {

    /** 探测线程的返回上限，正常路径下 tryLock 不阻塞，这个窗口只是保险。 */
    private static final long PROBE_TIMEOUT_SECONDS = 5L;

    private static boolean tryLockFromOtherThread(SegmentLock lock, Object key) throws Exception {
        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        new Thread(() -> {
            boolean got = lock.tryLock(key);
            acquired.set(got);
            // 谁取谁放：ReentrantLock 只能由持有线程释放。若在返回后由主线程 unlock，
            // 主线程并不持有这把段锁，直接抛 IllegalMonitorStateException（实测如此）。
            if (got) {
                lock.unlock(key);
            }
            done.countDown();
        }, "SegmentLockProbe").start();
        assertTrue(done.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS), "探测线程必须及时返回");
        return acquired.get();
    }

    @Test
    void heldSegmentBlocksOtherThreads() throws Exception {
        SegmentLock lock = new SegmentLock(4);
        lock.lock("shared");
        try {
            assertFalse(tryLockFromOtherThread(lock, "shared"), "同段必须互斥");
        } finally {
            lock.unlock("shared");
        }
        assertTrue(tryLockFromOtherThread(lock, "shared"), "释放后应可获取");
    }

    @Test
    void keysCollidingInSameSegmentAreExclusive() throws Exception {
        SegmentLock lock = new SegmentLock(4);
        // (0 & 0x7fffffff) % 4 == 0，(4 % 4) == 0：两个键落在同一把锁上
        lock.lock(0);
        try {
            assertFalse(tryLockFromOtherThread(lock, 4), " hashCode 取模同段，行为等同同一键");
            assertTrue(tryLockFromOtherThread(lock, 1), "不同段互不影响");
        } finally {
            lock.unlock(0);
        }
    }

    @Test
    void nullKeyUsesSharedDefaultLock() throws Exception {
        SegmentLock lock = new SegmentLock(4);
        lock.lock(null);
        try {
            assertFalse(tryLockFromOtherThread(lock, null), "null 键退化到一把共享默认锁");
        } finally {
            lock.unlock(null);
        }
    }

    @Test
    void tryLockOnFreeKeySucceeds() {
        SegmentLock lock = new SegmentLock(4);
        assertTrue(lock.tryLock("nobody"));
        lock.unlock("nobody");
    }

    @Test
    @DisplayName("F9（已修）：非正段数在构造期即抛 IllegalArgumentException，不再拖到首次取锁")
    void nonPositiveSegmentCountFailsAtConstruction() {
        // 修复口径：原钉住用例锁住"构造 0 不报错、首次 lock(key) 才抛 / by zero"的形状；
        // 现在期望正确行为——构造期校验，0 与负数都在 new 处即拒。
        assertThrows(IllegalArgumentException.class, () -> new SegmentLock(0));
        assertThrows(IllegalArgumentException.class, () -> new SegmentLock(-1));
    }

    @Test
    @DisplayName("F9 边界：1 段合法，取锁放锁照常工作")
    void singleSegmentIsLegal() {
        // 守卫若误写成 <=1，本用例即红——1 段是合法最小值，所有键同段但取模有定义。
        SegmentLock lock = new SegmentLock(1);
        assertDoesNotThrow(() -> lock.lock("k"));
        assertDoesNotThrow(() -> lock.unlock("k"));
    }
}
