package top.turboweb.commons.lock;

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
    @DisplayName("钉住 F9：SegmentLock(0) 构造不报错，首次按哈希取锁才抛 / by zero")
    void zeroSegmentCountFailsOnlyOnFirstUse() {
        SegmentLock lock = assertDoesNotThrow(() -> new SegmentLock(0), "构造期没有校验，不该在这里失败");
        ArithmeticException ex = assertThrows(ArithmeticException.class, () -> lock.lock("k"));
        assertTrue(ex.getMessage().contains("/ by zero"), ex.getMessage());
        assertThrows(ArithmeticException.class, () -> lock.tryLock("k"));
        // 而 null 键绕过了取模，照样能用——同一实例两种行为，是缺校验的直接后果
        assertDoesNotThrow(() -> lock.lock(null));
        assertDoesNotThrow(() -> lock.unlock(null));
    }
}
