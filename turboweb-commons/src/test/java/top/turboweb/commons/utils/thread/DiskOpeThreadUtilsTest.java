package top.turboweb.commons.utils.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import top.turboweb.commons.testkit.Awaits;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁盘操作线程池（全局单例）：init 幂等、提交与缓冲、哨兵搬运、背压返回 false。
 *
 * <p>F10：没有 shutdown/reset API，所以本类反射<b>只读</b>（isInit / EXECUTOR / TASKS），
 * 定序靠 {@code @Order}，不做任何静态回写——写回去也拿不回旧池。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiskOpeThreadUtilsTest {

    /** 全类唯一一次 init 的参数：2 条上限线程 + 1 格核心队列 + 2 格缓冲队列。 */
    private static final int CACHE_QUEUE = 2;
    private static final int CORE_QUEUE = 1;
    private static final int MAX_THREADS = 2;

    private static ThreadPoolExecutor executorAfterInit;

    private static Object readStatic(String name) {
        try {
            Field f = DiskOpeThreadUtils.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("读不到 " + name + "，本类用例无法取证", e);
        }
    }

    private static boolean isInit() {
        return (Boolean) readStatic("isInit");
    }

    private static int taskQueueSize() {
        @SuppressWarnings("unchecked")
        BlockingQueue<Runnable> tasks = (BlockingQueue<Runnable>) readStatic("TASKS");
        return tasks.size();
    }

    @Test
    @Order(1)
    @DisplayName("未初始化时提交：抛 ISE 而不是静默丢弃（F10 的另一面——也没法退回这个状态）")
    void executeBeforeInitThrowsIllegalState() {
        assertFalse(isInit(), "前置条件：本类是 commons 里唯一初始化它的用例");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DiskOpeThreadUtils.execute(() -> {
                }));
        assertEquals("DiskOpeThreadUtils has not been initialized yet", e.getMessage());
    }

    @Test
    @Order(2)
    void initBuildsOnceAndIsIdempotent() {
        DiskOpeThreadUtils.init(CACHE_QUEUE, CORE_QUEUE, MAX_THREADS);
        assertTrue(isInit(), "init 后标志位必须置起");
        executorAfterInit = (ThreadPoolExecutor) readStatic("EXECUTOR");
        assertNotNull(executorAfterInit);
        assertEquals(MAX_THREADS, executorAfterInit.getMaximumPoolSize());
        assertEquals(1, executorAfterInit.getCorePoolSize(), "核心线程数固定为 1（源码写死）");

        // 二次 init 换参数也必须原样返回：这就是 :38-39 那条外层 return
        DiskOpeThreadUtils.init(9, 9, 9);
        assertSame(executorAfterInit, readStatic("EXECUTOR"), "重复 init 不得重建全局池");
    }

    @Test
    @Order(3)
    void taskSubmittedAfterInitRuns() throws Exception {
        DiskOpeThreadUtils.init(CACHE_QUEUE, CORE_QUEUE, MAX_THREADS);
        CountDownLatch ran = new CountDownLatch(1);
        assertTrue(DiskOpeThreadUtils.execute(ran::countDown));
        assertTrue(ran.await(5, TimeUnit.SECONDS), "哨兵/工作线程必须在 5s 内跑掉任务");
    }

    @Test
    @Order(4)
    @DisplayName("三层容量全占满后返回 false，且缓冲里的任务仍由哨兵搬走")
    void overflowIsBufferedThenMovedBySentinel() throws Exception {
        DiskOpeThreadUtils.init(CACHE_QUEUE, CORE_QUEUE, MAX_THREADS);
        CountDownLatch blockerGate = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        Runnable blocker = () -> {
            try {
                blockerGate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            assertTrue(DiskOpeThreadUtils.execute(blocker));
            assertTrue(DiskOpeThreadUtils.execute(blocker));
            assertTrue(DiskOpeThreadUtils.execute(blocker));

            assertTrue(DiskOpeThreadUtils.execute(executed::incrementAndGet), "第 4 个应进缓冲队列");
            assertTrue(Awaits.awaitUntil(DiskOpeThreadUtilsTest::taskQueueIsEmpty, 2_000),
                    "哨兵必须已把它取走并卡在退避里——这是缓冲队列腾空的观测点");
            assertTrue(DiskOpeThreadUtils.execute(executed::incrementAndGet), "缓冲 1/2");
            assertTrue(DiskOpeThreadUtils.execute(executed::incrementAndGet), "缓冲 2/2");
            assertFalse(DiskOpeThreadUtils.execute(executed::incrementAndGet), "皆满必须返回 false");
        } finally {
            // 全局池 + @Order 定序的含义：本条中途失败若不放开 gate，blocker 会占死两条
            // worker 直到 10s 自身超时，把后面的用例一起拖红（实测如此：变异 #4 让
            // workerThreadsAreDaemonAndNamed 级联失败），红因就归不到变异本身。
            blockerGate.countDown();
        }
        assertTrue(Awaits.awaitUntil(() -> executed.get() == 3, 5_000),
                () -> "3 个被接受的任务都要被执行，实际 " + executed.get());
    }

    private static boolean taskQueueIsEmpty() {
        return taskQueueSize() == 0;
    }

    @Test
    @Order(5)
    void workerThreadsAreDaemonAndNamed() {
        DiskOpeThreadUtils.init(CACHE_QUEUE, CORE_QUEUE, MAX_THREADS);
        // 前面用例已建过线程：InternalThreadFactory 的命名与 daemon 属性可在池的线程工厂行为上取证
        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger seen = new AtomicInteger();
        assertTrue(DiskOpeThreadUtils.execute(() -> {
            Thread t = Thread.currentThread();
            if (t.isDaemon() && t.getName().startsWith("DiskOpe-Thread-")) {
                seen.incrementAndGet();
            }
            ran.countDown();
        }));
        try {
            assertTrue(ran.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        assertEquals(1, seen.get(), "磁盘操作线程必须是 daemon 且带 DiskOpe-Thread- 前缀");
    }
}
