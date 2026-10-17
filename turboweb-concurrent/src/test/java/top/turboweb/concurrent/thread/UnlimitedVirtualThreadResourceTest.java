package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import top.turboweb.concurrent.thread.VirtualThreadResource.FinallyType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link UnlimitedVirtualThreadResource} 的构造校验与无资源控制提交语义。
 *
 * <p>非受限实现不排队、不拒绝：每个提交即刻返回承载虚拟线程，收尾回调不会以拒绝情形触发，
 * 任务结束后以执行完毕情形触发一次；执行与否的取证靠任务内闩与计数，线程名断言经任务体内自取。
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class UnlimitedVirtualThreadResourceTest {

    /** 不应发生的拒绝：收尾情形为 REJECTED 直接判失败，EXECUTED 为任务正常收尾。 */
    private static VirtualThreadResource.FinallyHandler neverRejected() {
        return type -> {
            if (type == FinallyType.REJECTED) {
                fail("非受限实现不应触发拒绝收尾");
            }
        };
    }

    @Test
    @DisplayName("线程名必传：null 线程名构造即拒绝")
    void constructorRequiresThreadName() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new UnlimitedVirtualThreadResource(null));
        assertEquals("线程名不能为null", error.getMessage());
        assertNotNull(new UnlimitedVirtualThreadResource("uvt-ok"));
    }

    @Test
    @DisplayName("run 的 null 参数先行拒绝")
    void runRejectsNullTaskAndHandler() {
        UnlimitedVirtualThreadResource resource = new UnlimitedVirtualThreadResource("uvt-args");

        assertThrows(NullPointerException.class, () -> resource.run(null, neverRejected()));
        assertThrows(NullPointerException.class, () -> resource.run(() -> {
        }, null));
    }

    @Test
    @DisplayName("提交即刻受理：任务在命名虚拟线程执行，返回承载线程，不触发拒绝")
    void runStartsTaskOnNamedVirtualThread() throws InterruptedException {
        UnlimitedVirtualThreadResource resource = new UnlimitedVirtualThreadResource("uvt-worker");
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread worker = resource.run(() -> {
            assertTrue(Thread.currentThread().isVirtual(), "承载线程应为虚拟线程");
            observed.set(1);
            done.countDown();
        }, finishes::add);

        assertNotNull(worker, "非受限实现应始终返回承载线程");
        assertTrue(worker.isVirtual());
        assertEquals("uvt-worker", worker.getName(), "承载线程应使用配置名称");
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertEquals(1, observed.get());
        worker.join(5_000);
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务结束后收尾回调以执行完毕情形触发一次");
    }

    @Test
    @DisplayName("任务抛出异常不阻断后续提交：异常随线程默认处理器输出，资源侧无任何残留")
    void taskFailureDoesNotBlockLaterSubmissions() throws InterruptedException {
        UnlimitedVirtualThreadResource resource = new UnlimitedVirtualThreadResource("uvt-fail");
        CountDownLatch done = new CountDownLatch(1);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread failing = resource.run(() -> {
            throw new IllegalStateException("boom");
        }, finishes::add);
        assertNotNull(failing);
        failing.join(5_000);
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务抛出异常后仍以执行完毕情形收尾");

        Thread next = resource.run(done::countDown, neverRejected());
        assertNotNull(next, "前一任务失败不应影响后续受理");
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("并发批量提交全部即刻受理：每枚任务独立执行且全部完成")
    void concurrentSubmissionsAllExecuteImmediately() throws InterruptedException {
        UnlimitedVirtualThreadResource resource = new UnlimitedVirtualThreadResource("uvt-bulk");
        int total = 16;
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger executed = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        List<Thread> workers = new CopyOnWriteArrayList<>();
        for (int i = 0; i < total; i++) {
            Thread worker = resource.run(() -> {
                executed.incrementAndGet();
                done.countDown();
            }, finishes::add);
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
