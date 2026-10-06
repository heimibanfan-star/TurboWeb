package top.turboweb.commons.utils.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.testkit.Awaits;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹性线程池：参数校验、提交语义、背压返回 false、关闭不丢任务。
 */
class ElasticThreadPoolTest {

    /** 读私有缓冲队列长度，只为把"搬运线程已取走手上任务"变成可断言的观测点。 */
    private static int cacheSize(ElasticThreadPool pool) {
        try {
            Field f = ElasticThreadPool.class.getDeclaredField("cacheQueue");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            BlockingQueue<Runnable> queue = (BlockingQueue<Runnable>) f.get(pool);
            return queue.size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("读不到 cacheQueue，本类的背压用例无法取证", e);
        }
    }

    @Test
    void nonPositiveArgumentsAreRejected() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticThreadPool(0, 1, 1));
        assertEquals("cacheQueue must be greater than 0", e1.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticThreadPool(1, 0, 1));
        assertEquals("coreQueue must be greater than 0", e2.getMessage());
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticThreadPool(1, 1, 0));
        assertEquals("maxThreadNum must be greater than 0", e3.getMessage());
    }

    @Test
    void nullTaskThrowsNpeBeforeAnyQueueing() {
        ElasticThreadPool pool = new ElasticThreadPool(4, 1, 2);
        try {
            NullPointerException e = assertThrows(NullPointerException.class, () -> pool.execute(null));
            assertEquals("task can not be null", e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void submittedTaskRunsAndIsReportedAccepted() throws Exception {
        ElasticThreadPool pool = new ElasticThreadPool(4, 1, 2);
        CountDownLatch ran = new CountDownLatch(1);
        try {
            assertTrue(pool.execute(ran::countDown));
            assertTrue(ran.await(5, TimeUnit.SECONDS), "任务必须在 5s 内被执行");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("try-with-resources 走 ThreadPool 接口的 default close()，最终落到 shutdown()")
    void defaultCloseShutsDownPool() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        ThreadPool pool = new ElasticThreadPool(4, 1, 2);
        try (pool) {
            assertTrue(pool.execute(executed::incrementAndGet));
            assertFalse(pool.isShutdown(), "使用期间不得是关闭状态");
        }
        assertTrue(pool.isShutdown(), "close() 只代理 shutdown()，标志位必须置起来");
        // 必须先等终止再数执行次数：shutdown() 只是"不再接新任务"，核心队列里那个任务
        // 由 worker 线程异步跑，close() 返回时它可能还没执行——直接断言计数是抖的。
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "5s 内必须跑完已接受的任务");
        assertEquals(1, executed.get(), "已接受的任务在关闭后仍要跑完");
    }

    @Test
    void executeAfterShutdownThrowsRee() {
        ElasticThreadPool pool = new ElasticThreadPool(4, 1, 2);
        pool.shutdown();
        RejectedExecutionException e = assertThrows(RejectedExecutionException.class,
                () -> pool.execute(() -> {
                }));
        assertEquals("ElasticThreadPool has been shut down", e.getMessage());
        assertTrue(pool.isShutdown());
    }

    @Test
    @DisplayName("钉住 F7：搬运线程抱着的任务也不许丢，shutdown 后已接受数 == 已执行数")
    void shutdownRunsEveryAcceptedTaskUnderBackpressure() throws Exception {
        // 2 条上限线程 + 1 格核心队列 + 2 格缓冲：三层全占满后才开始取证
        ElasticThreadPool pool = new ElasticThreadPool(2, 1, 2);
        CountDownLatch blockerGate = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        Runnable blocker = () -> {
            try {
                blockerGate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        // 占满 worker1、核心队列、worker2：此后任何提交都只会进缓冲或被拒
        assertTrue(pool.execute(blocker));
        assertTrue(pool.execute(blocker));
        assertTrue(pool.execute(blocker));

        assertTrue(pool.execute(executed::incrementAndGet), "第 4 个任务应进缓冲队列");
        assertTrue(Awaits.awaitUntil(() -> cacheSize(pool) == 0, 2_000),
                "搬运线程必须已把它取走并卡在退避重试里——这是缓冲队列腾空的观测点");
        assertTrue(pool.execute(executed::incrementAndGet), "缓冲 1/2");
        assertTrue(pool.execute(executed::incrementAndGet), "缓冲 2/2");
        assertFalse(pool.execute(executed::incrementAndGet), "核心队列与缓冲队列皆满时必须返回 false，不许抛异常");

        blockerGate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "本地池可用 awaitTermination 作为收尾同步，不是对服务端池的终止断言");
        assertEquals(3, executed.get(), () -> "3 个被接受的任务全部执行；实际 " + executed.get()
                + " 个——若少一个，说明搬运线程手上那个任务在 shutdown 序列里滞留缓冲队列");
    }
}
