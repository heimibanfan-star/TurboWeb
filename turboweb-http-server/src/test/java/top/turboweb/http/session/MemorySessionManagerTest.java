package top.turboweb.http.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemorySessionManager} 的会话 CRUD、并发创建、GC 启动守卫与过期回收。
 *
 * <p>过期回收的“该不该回收”是确定量（注入 maxNotUseTime=-1 使会话必然过期）；
 * 只有“等调度器跑第一轮”依赖真实时钟——依赖调度器的用例均以有界轮询等待，不做
 * sleep 猜计数、不断言线程终止。
 */
class MemorySessionManagerTest {

    @Test
    @DisplayName("显式 sessionId 的增删改查与名称")
    void explicitSessionRoundTrips() {
        MemorySessionManager manager = new MemorySessionManager();
        assertTrue(manager.createSessionMap("s"), "首次创建成功");
        assertFalse(manager.createSessionMap("s"), "重复创建返回 false 且不覆盖");

        manager.setAttr("s", "name", "tom");
        assertEquals("tom", manager.getAttr("s", "name"));
        assertEquals("tom", manager.getAttr("s", "name", String.class));
        assertTrue(manager.exist("s"));

        manager.setAttr("s", "temp", "v", -1L);
        assertNull(manager.getAttr("s", "temp"), "负过期时间 = 立即失效");

        manager.remAttr("s", "name");
        assertNull(manager.getAttr("s", "name"));

        assertEquals("memory session manager", manager.sessionManagerName());
    }

    @Test
    @DisplayName("对不存在的会话做任何操作都是无副作用空操作")
    void operationsOnUnknownSessionAreNoOps() {
        MemorySessionManager manager = new MemorySessionManager();
        assertFalse(manager.exist("nope"));
        assertNull(manager.getAttr("nope", "k"), "会话不存在直接返回 null");
        assertNull(manager.getAttr("nope", "k", String.class));
        assertDoesNotThrow(() -> manager.setAttr("nope", "k", "v"));
        assertDoesNotThrow(() -> manager.setAttr("nope", "k", "v", 1L));
        assertDoesNotThrow(() -> manager.remAttr("nope", "k"));
        assertDoesNotThrow(() -> manager.expireAt("nope"));
        assertFalse(manager.exist("nope"), "全部空操作后依然不存在");
    }

    @Test
    @DisplayName("并发创建同一 sessionId 恰好一个线程成功（只断言不变式）")
    void concurrentCreateHasExactlyOneWinner() throws Exception {
        MemorySessionManager manager = new MemorySessionManager();
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (manager.createSessionMap("same")) {
                            success.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "所有创建线程应在超时内结束");
        }
        assertEquals(1, success.get(), "分段锁 + 双检必须保证同一 sessionId 只有一个创建者拿到 true");
        assertTrue(manager.exist("same"));
    }

    @Test
    @DisplayName("sessionGC 的启动标志由 compareAndSet 守卫，重复调用不再启动")
    void sessionGcStartFlagIsSetOnce() throws Exception {
        MemorySessionManager manager = new MemorySessionManager();
        assertFalse(readStartGc(manager), "尚未启动 GC，标志应为 false");

        // checkTime 取 60s：调度器不会在本用例期间触发，只验证启动守卫与标志位，不涉线程终止。
        manager.sessionGC(60_000L, 60_000L, Long.MAX_VALUE);
        assertTrue(readStartGc(manager), "首次调用应把 isStartGC 置为 true");

        manager.sessionGC(60_000L, 60_000L, Long.MAX_VALUE);
        assertTrue(readStartGc(manager), "重复调用被 compareAndSet 守卫拦住，标志保持不变");
    }

    @Test
    @DisplayName("回收：阈值到点后，已过期会话被移除")
    void sessionGcRemovesExpiredSession() {
        // 代价说明：注入 checkTime=10ms 的极小检查间隔，用有界轮询等调度器首次触发。
        // “该不该回收”是确定量——会话在构造函数里 expireAt 记时，注入 maxNotUseTime=-1 使其必然过期；
        // 真实时钟只用于等调度器跑第一轮（通常 <50ms），不做 sleep 猜计数、不断言线程终止。
        MemorySessionManager manager = new MemorySessionManager();
        assertTrue(manager.createSessionMap("expired"));
        manager.setAttr("expired", "k", "v");
        manager.sessionGC(10L, -1L, 1L);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (manager.exist("expired") && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertFalse(manager.exist("expired"), "超过 maxNotUseTime 的会话应被 GC 移除");
    }

    @Test
    @DisplayName("钉住 F2：session-gc 调度器没有停止入口，无法登记进 ScopedResource")
    void sessionGcSchedulerHasNoStopEntry() {
        // 现象（spec §8 候选 2）：sessionGC 把 ScheduledExecutorService 建在方法局部变量里，
        // 类既不实现 AutoCloseable，也没有 close/shutdown/stop 之类停止入口 ⇒ 外部无法回收该守护
        // 线程池，因此无法登记进 ScopedResource，属资源泄漏。
        // 本用例不断言线程是否终止（不可确定），只断言“停止入口不存在”这一 API 事实。
        // 钉住 F2：若将来修正，本用例应改为期望正确行为（若将来补上停止钩子，本用例会变红，届时改为断言该入口存在并调用它）。
        assertFalse(AutoCloseable.class.isAssignableFrom(MemorySessionManager.class),
                "一旦实现 AutoCloseable 即说明已补停止钩子");
        Set<String> names = Arrays.stream(MemorySessionManager.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("close"), "不应存在 close 入口");
        assertFalse(names.contains("shutdown"), "不应存在 shutdown 入口");
        assertFalse(names.contains("stop"), "不应存在 stop 入口");
    }

    @Test
    @DisplayName("安全作用域持在本实例读锁上：可重入且逐层解除、写侧全程互斥")
    void safeScopeSharesReadersAndExcludesWriter() throws Exception {
        MemorySessionManager manager = new MemorySessionManager();
        ReentrantReadWriteLock instanceLock = readWriteLockOf(manager);

        manager.sessionSafeScope(outer -> {
            // 同一线程可重入：内层作用域直接执行，不阻塞不抛
            manager.sessionSafeScope(inner -> {
                assertFalse(instanceLock.writeLock().tryLock(),
                        "嵌套作用域持有期间本实例写锁不可获得——GC 写侧与作用域互斥正是这条通路");
                return null;
            });
            // 内层已退出、外层仍持读锁
            assertFalse(instanceLock.writeLock().tryLock(), "内层退出后外层仍持读锁，写侧依旧不可获得");
            return null;
        });

        assertTrue(instanceLock.writeLock().tryLock(), "外层退出后写侧即可获得");
        instanceLock.writeLock().unlock();
    }

    @Test
    @DisplayName("GC 每轮重新获得写锁：第二批过期会话同样被回收")
    void sessionGcReacquiresWriteLockEachCycle() throws Exception {
        MemorySessionManager manager = new MemorySessionManager();
        assertTrue(manager.createSessionMap("first"));
        manager.sessionGC(10L, -1L, 1L);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (manager.exist("first") && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertFalse(manager.exist("first"), "首轮回收应移除过期会话");

        assertTrue(manager.createSessionMap("second"));
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (manager.exist("second") && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertFalse(manager.exist("second"), "若上一轮退出时未释放写锁，本轮将永久阻塞，第二个会话永远收不掉");

        // 同线程重入会把"删掉 unlock()"的变异伪装成照常回收（持锁计数只增不减仍自锁自解），
        // 必须换另一线程取证写锁真被释放：写锁若被 GC 线程滞留，本线程永远进不了读侧。
        ReentrantReadWriteLock instanceLock = readWriteLockOf(manager);
        boolean readerAdmitted = false;
        long admitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!readerAdmitted && System.nanoTime() < admitDeadline) {
            if (instanceLock.readLock().tryLock()) {
                readerAdmitted = true;
            } else {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        assertTrue(readerAdmitted, "GC 每轮退出必须释放写锁，否则其他线程永久无法进入读侧");
        instanceLock.readLock().unlock();
    }

    @Test
    @DisplayName("安全作用域内 GC 移不掉过期会话，作用域退出后恢复回收")
    void sessionGcWaitsForSafeScopeExit() {
        MemorySessionManager manager = new MemorySessionManager();
        assertTrue(manager.createSessionMap("expired"));
        manager.setAttr("expired", "k", "v");

        manager.sessionSafeScope(scope -> {
            manager.sessionGC(10L, -1L, 1L);
            // 放行调度器多轮触发：GC 的写锁申请被作用域读锁阻塞，回收进不了临界区
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
            assertTrue(manager.exist("expired"), "作用域持有期间 GC 拿不到写锁，会话不应被移除");
            return null;
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (manager.exist("expired") && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertFalse(manager.exist("expired"), "作用域退出后 GC 获得写锁，回收照常执行");
    }

    /** 只读反射：isStartGC 是 private final AtomicBoolean，无公共 getter，只能读标志位本身。 */
    private static boolean readStartGc(MemorySessionManager manager) throws Exception {
        Field field = MemorySessionManager.class.getDeclaredField("isStartGC");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(manager)).get();
    }

    /** 只读反射：readWriteLock 是 private final 字段，公共 API 不给锁对象，互斥取证只能读本实例锁。 */
    private static ReentrantReadWriteLock readWriteLockOf(MemorySessionManager manager) throws Exception {
        Field field = MemorySessionManager.class.getDeclaredField("readWriteLock");
        field.setAccessible(true);
        return (ReentrantReadWriteLock) field.get(manager);
    }
}
