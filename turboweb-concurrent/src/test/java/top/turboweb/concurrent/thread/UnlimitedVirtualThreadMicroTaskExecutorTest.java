package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallyType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 非受限实现（{@link VirtualThreadMicroTaskExecutor#ofUnlimited}）的构造校验与无资源控制提交语义。
 *
 * <p>非受限实现不排队、不因资源受限而未受理：每个提交即刻返回承载虚拟线程，
 * 任务结束后以执行完毕情形收尾一次；执行与否的取证靠任务内闩与计数，线程名断言经任务体内自取。
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class UnlimitedVirtualThreadMicroTaskExecutorTest {

    /** 不应发生的未受理：收尾情形为 NOT_ACCEPT 直接判失败，EXECUTED 为任务正常收尾。 */
    private static MicroTaskExecutor.FinallyHandler neverNotAccepted() {
        return signal -> {
            if (signal.finallyType() == FinallyType.NOT_ACCEPT) {
                fail("非受限实现不应触发未受理收尾");
            }
        };
    }

    @Test
    @DisplayName("线程名必传：null 线程名构造即拒绝")
    void constructorRequiresThreadName() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> VirtualThreadMicroTaskExecutor.ofUnlimited(null));
        assertEquals("threadName must not be null", error.getMessage());
        assertNotNull(VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-ok"));
    }

    @Test
    @DisplayName("观测读数按下放开值：非受限标志为假，资源数不设上限，挂起数与公平标志恒为零/假")
    void unlimitedExposesOpenDefaults() {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-defaults");

        assertFalse(resource.isLimited(), "非受限实现不应是受限实现");
        assertEquals(Integer.MAX_VALUE, resource.availableResources(), "非受限实现不设资源上限");
        assertEquals(0, resource.suspendedThreads(), "非受限实现不排队，挂起数恒为 0");
        assertFalse(resource.isFair(), "非受限实现无等待队列，公平标志恒为假");
    }

    @Test
    @DisplayName("run 的 null 参数先行拒绝")
    void runRejectsNullTaskAndHandler() {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-args");

        assertThrows(NullPointerException.class, () -> resource.run(null, neverNotAccepted()));
        assertThrows(NullPointerException.class, () -> resource.run(() -> {
        }, null));
    }

    @Test
    @DisplayName("submit 的 null 参数由 run 的参数校验先行拒绝")
    void submitRejectsNullTaskAndHandler() {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-submit-args");

        assertThrows(NullPointerException.class, () -> resource.submit(null, neverNotAccepted()));
        assertThrows(NullPointerException.class, () -> resource.submit(() -> {
        }, null));
    }

    @Test
    @DisplayName("提交即刻受理：任务在命名虚拟线程执行，返回承载线程，不触发未受理收尾")
    void runStartsTaskOnNamedVirtualThread() throws InterruptedException {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-worker");
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread worker = resource.run(() -> {
            assertTrue(Thread.currentThread().isVirtual(), "承载线程应为虚拟线程");
            observed.set(1);
            done.countDown();
        }, signal -> finishes.add(signal.finallyType()));

        assertNotNull(worker, "非受限实现应始终返回承载线程");
        assertTrue(worker.isVirtual());
        assertEquals("uvt-worker", worker.getName(), "承载线程应使用配置名称");
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertEquals(1, observed.get());
        worker.join(5_000);
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务结束后收尾回调以执行完毕情形触发一次");
    }

    @Test
    @DisplayName("submit 提交即受理：任务在命名虚拟线程执行，以执行完毕情形收尾")
    void submitStartsTaskOnNamedVirtualThread() throws InterruptedException {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-submit");
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        resource.submit(() -> {
            assertTrue(Thread.currentThread().isVirtual(), "承载线程应为虚拟线程");
            assertEquals("uvt-submit", Thread.currentThread().getName(), "承载线程应使用配置名称");
            observed.set(1);
            done.countDown();
        }, signal -> {
            finishes.add(signal.finallyType());
            finished.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertEquals(1, observed.get());
        assertTrue(finished.await(5, TimeUnit.SECONDS), "收尾回调应在任务结束后触发");
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务结束后收尾回调以执行完毕情形触发一次");
    }

    @Test
    @DisplayName("任务抛出异常不阻断后续提交：异常随线程默认处理器输出，资源侧无任何残留")
    void taskFailureDoesNotBlockLaterSubmissions() throws InterruptedException {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-fail");
        CountDownLatch done = new CountDownLatch(1);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread failing = resource.run(() -> {
            throw new IllegalStateException("boom");
        }, signal -> finishes.add(signal.finallyType()));
        assertNotNull(failing);
        failing.join(5_000);
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务抛出异常后仍以执行完毕情形收尾");

        Thread next = resource.run(done::countDown, neverNotAccepted());
        assertNotNull(next, "前一任务失败不应影响后续受理");
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("并发批量提交全部即刻受理：每枚任务独立执行且全部完成")
    void concurrentSubmissionsAllExecuteImmediately() throws InterruptedException {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofUnlimited("uvt-bulk");
        int total = 16;
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger executed = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        List<Thread> workers = new CopyOnWriteArrayList<>();
        for (int i = 0; i < total; i++) {
            Thread worker = resource.run(() -> {
                executed.incrementAndGet();
                done.countDown();
            }, signal -> finishes.add(signal.finallyType()));
            assertNotNull(worker, "非受限实现任何提交都应受理");
            workers.add(worker);
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "全部任务都应完成");
        assertEquals(total, executed.get());
        for (Thread worker : workers) {
            worker.join(5_000);
        }
        assertEquals(total, finishes.size(), "每次提交恰好一次收尾");
        assertTrue(finishes.stream().allMatch(type -> type == FinallyType.EXECUTED),
                "全部提交均以执行完毕情形收尾，不应出现拒绝");
    }
}
