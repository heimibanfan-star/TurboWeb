package top.turboweb.commons.utils.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 虚拟线程工具：startThread 的命名虚拟线程 + 全局共享池的身份稳定。
 */
class VirtualThreadsTest {

    @Test
    void startThreadRunsTaskOnNamedVirtualThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Thread> seen = new AtomicReference<>();
        VirtualThreads.startThread(() -> {
            seen.set(Thread.currentThread());
            done.countDown();
        }, "probe-named-virtual");
        assertTrue(done.await(5, TimeUnit.SECONDS), "虚拟线程必须在 5s 内跑起来");
        Thread t = seen.get();
        assertNotNull(t, "任务体必须真的执行过");
        assertTrue(t.isVirtual(), "startThread 必须落在虚拟线程上");
        assertEquals("probe-named-virtual", t.getName(), "线程名必须由调用方指定");
    }

    @Test
    void poolIsSharedAndRunsTasksOnVirtualThreads() throws Exception {
        ExecutorService pool = VirtualThreads.getPool();
        assertSame(pool, VirtualThreads.getPool(), "共享池不得每次新建（WebSocket 分发器与 HTTP 调度器复用同一个）");

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Thread> seen = new AtomicReference<>();
        pool.execute(() -> {
            seen.set(Thread.currentThread());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(seen.get().isVirtual(), "共享池必须是虚拟线程执行器");
    }
}
