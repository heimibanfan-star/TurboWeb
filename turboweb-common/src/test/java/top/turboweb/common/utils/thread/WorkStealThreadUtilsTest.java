package top.turboweb.common.utils.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作窃取池的懒加载单例语义。
 */
class WorkStealThreadUtilsTest {

    @Test
    void getExecutorServiceIsSameInstanceAcrossCalls() {
        ExecutorService first = WorkStealThreadUtils.getExecutorService();
        assertSame(first, WorkStealThreadUtils.getExecutorService(), "懒加载后必须复用同一实例");
    }

    @Test
    void executeRunsTaskOnSharedPool() throws Exception {
        // 走 execute(Runnable) 这条委托链，而不是再取一次 getExecutorService 比自己
        CountDownLatch ran = new CountDownLatch(1);
        WorkStealThreadUtils.execute(ran::countDown);
        assertTrue(ran.await(5, SECONDS), "任务必须在 5s 内被执行");
    }
}
