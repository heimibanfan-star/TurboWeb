package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallySignal;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallyType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ForkJoinMicroTaskExecutor} 的工厂语义与提交契约。
 * <p>共享实例为双重检查锁懒加载单例，并行度取默认值（CPU 核心数减一）；隔离实例每次新建独立池，
 * 并行度与工作线程名前缀按给定值。提交任务在工作线程执行，结束后以执行完毕情形收尾一次；
 * 任务未获受理、任务未启动时以未受理情形收尾一次，异常或错误原样抛给提交方。
 * 验证靠任务体内自取线程名与闩/计数取证。</p>
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class ForkJoinMicroTaskExecutorTest {

    /** 默认并行度：与实现同一口径。 */
    private static int defaultParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    @Test
    @DisplayName("共享实例为单例：重复调用返回同一实例，并行度与工作线程名取默认口径")
    void sharedInstanceIsSingletonWithDefaultParallelismAndNaming() throws Exception {
        ForkJoinMicroTaskExecutor first = ForkJoinMicroTaskExecutor.ofShared();
        ForkJoinMicroTaskExecutor second = ForkJoinMicroTaskExecutor.ofShared();

        assertSame(first, second, "共享实例应全局唯一");
        assertEquals(defaultParallelism(), first.parallelism(), "共享实例并行度应为默认并行度");

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> workerName = new AtomicReference<>();
        first.submit(() -> {
            workerName.set(Thread.currentThread().getName());
            done.countDown();
        }, type -> {
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "共享池应受理并执行任务");
        assertTrue(workerName.get().startsWith("forkjoin-shared-micro-task-"),
                "共享工作线程应按共享前缀加序号命名，实际为 " + workerName.get());
    }

    @Test
    @DisplayName("并发首次访问只创建一次共享实例")
    void concurrentFirstAccessCreatesExactlyOneSharedInstance() throws Exception {
        resetSharedForFirstAccess();
        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<ForkJoinMicroTaskExecutor> instances = new CopyOnWriteArrayList<>();
        List<Thread> accessors = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread accessor = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                instances.add(ForkJoinMicroTaskExecutor.ofShared());
                done.countDown();
            });
            accessors.add(accessor);
            accessor.start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS), "所有访问线程应就绪");
        go.countDown();
        for (Thread accessor : accessors) {
            accessor.join(5_000);
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "所有访问线程都应完成");

        assertEquals(1, new java.util.HashSet<>(instances).size(), "并发首次访问应只创建一个共享实例");
        assertSame(ForkJoinMicroTaskExecutor.ofShared(), instances.get(0), "后续访问应返回同一实例");
    }

    @Test
    @DisplayName("隔离实例参数校验：名称必传，线程数必须大于 0")
    void ofIsolatedValidatesNameAndThreadCount() {
        NullPointerException nullName = assertThrows(NullPointerException.class,
                () -> ForkJoinMicroTaskExecutor.ofIsolated(null, 2));
        assertEquals("threadName must not be null", nullName.getMessage());
        NullPointerException nullNameOnly = assertThrows(NullPointerException.class,
                () -> ForkJoinMicroTaskExecutor.ofIsolated(null));
        assertEquals("threadName must not be null", nullNameOnly.getMessage());

        for (int invalid : new int[]{0, -1}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ForkJoinMicroTaskExecutor.ofIsolated("iso-bad", invalid));
            assertEquals("threadCount must be greater than 0", error.getMessage());
        }
    }

    @Test
    @DisplayName("双参数重载：每次新建独立池，并行度与工作线程前缀按给定值")
    void ofIsolatedWithCountCreatesIndependentPool() throws Exception {
        ForkJoinMicroTaskExecutor first = ForkJoinMicroTaskExecutor.ofIsolated("iso-count-a", 3);
        ForkJoinMicroTaskExecutor second = ForkJoinMicroTaskExecutor.ofIsolated("iso-count-a", 3);

        assertNotSame(first, second, "隔离实例每次调用都应新建独立池");
        assertEquals(3, first.parallelism(), "并行度应按给定线程数");

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> workerName = new AtomicReference<>();
        first.submit(() -> {
            workerName.set(Thread.currentThread().getName());
            done.countDown();
        }, type -> {
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应在独立池中执行");
        assertTrue(workerName.get().startsWith("iso-count-a-"), "工作线程应按给定前缀加序号命名，实际为 "
                + workerName.get());
    }

    @Test
    @DisplayName("单名称重载：并行度取默认值，每次调用新建独立池")
    void ofIsolatedWithNameOnlyUsesDefaultParallelism() {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-default-a");

        assertEquals(defaultParallelism(), executor.parallelism(), "只给名称应按默认并行度创建");
        assertNotSame(executor, ForkJoinMicroTaskExecutor.ofIsolated("iso-default-a"), "每次调用都应新建独立池");
    }

    @Test
    @DisplayName("提交任务在命名工作线程执行，结束后在执行任务的线程以执行完毕情形收尾一次")
    void submitExecutesTaskThenFinishesWithExecuted() throws Exception {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-exec", 1);
        CountDownLatch taskRan = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> workerName = new AtomicReference<>();
        AtomicReference<String> handlerThreadName = new AtomicReference<>();
        AtomicReference<FinallySignal.CallbackThread> finallyThreadSide = new AtomicReference<>();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        executor.submit(() -> {
            workerName.set(Thread.currentThread().getName());
            taskRan.countDown();
        }, signal -> {
            handlerThreadName.set(Thread.currentThread().getName());
            finallyThreadSide.set(signal.callbackThread());
            finishes.add(signal.finallyType());
            finished.countDown();
        });

        assertTrue(taskRan.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertTrue(finished.await(5, TimeUnit.SECONDS), "收尾回调应在任务结束后触发");
        assertEquals(workerName.get(), handlerThreadName.get(), "执行完毕收尾应在执行任务的线程中回调");
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "以执行完毕情形收尾一次");
        assertEquals(FinallySignal.CallbackThread.WORKER, finallyThreadSide.get(), "执行完毕收尾应由工作线程发起");
    }

    @Test
    @DisplayName("任务抛出异常仍以执行完毕情形收尾，且不阻断后续提交")
    void taskFailureFinishesWithExecutedAndDoesNotBlockLaterSubmissions() throws Exception {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-fail", 1);
        CountDownLatch finished = new CountDownLatch(1);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        executor.submit(() -> {
            throw new IllegalStateException("boom");
        }, signal -> {
            finishes.add(signal.finallyType());
            finished.countDown();
        });

        assertTrue(finished.await(5, TimeUnit.SECONDS), "异常任务的收尾应触发");
        assertEquals(List.of(FinallyType.EXECUTED), finishes, "任务抛出异常仍以执行完毕情形收尾");

        CountDownLatch nextRan = new CountDownLatch(1);
        executor.submit(nextRan::countDown, type -> {
        });
        assertTrue(nextRan.await(5, TimeUnit.SECONDS), "前一任务失败不应影响后续受理");
    }

    @Test
    @DisplayName("任务池终止后提交：任务未启动，以未受理情形收尾且拒绝异常原样抛出")
    void submitAfterPoolTerminationFinishesWithNotAcceptedAndRethrows() throws Exception {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-reject", 2);
        ForkJoinPool pool = poolOf(executor);
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "任务池应终止");

        CountDownLatch taskRan = new CountDownLatch(1);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();
        AtomicReference<FinallySignal> unstarted = new AtomicReference<>();
        assertThrows(RejectedExecutionException.class,
                () -> executor.submit(taskRan::countDown, signal -> {
                    unstarted.set(signal);
                    finishes.add(signal.finallyType());
                }));

        assertEquals(1, taskRan.getCount(), "已终止池的提交不应执行任务");
        assertEquals(List.of(FinallyType.NOT_ACCEPT), finishes, "任务未启动，以未受理情形收尾一次");
        assertEquals(FinallySignal.CallbackThread.SUBMITTER, unstarted.get().callbackThread(),
                "池拒绝受理的收尾应发生在提交线程");
    }

    @Test
    @DisplayName("受理阶段抛出 Error：任务未启动，以未受理情形收尾且错误原样抛给提交方")
    void acceptanceErrorFinishesWithNotAcceptedAndRethrows() {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-oom", 1);
        OutOfMemoryError boom = new OutOfMemoryError("test-oom");
        replacePool(executor, new ForkJoinPool() {
            @Override
            public void execute(Runnable task) {
                throw boom;
            }
        });

        List<FinallyType> finishes = new CopyOnWriteArrayList<>();
        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                () -> executor.submit(() -> {
                }, signal -> finishes.add(signal.finallyType())));
        assertSame(boom, thrown, "受理阶段错误应原样抛给提交方");
        assertEquals(List.of(FinallyType.NOT_ACCEPT), finishes, "任务未启动，以未受理情形收尾一次");
    }

    @Test
    @DisplayName("submit 的 null 参数先行拒绝")
    void submitRejectsNullArguments() {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofIsolated("iso-args", 1);

        NullPointerException nullTask = assertThrows(NullPointerException.class,
                () -> executor.submit(null, type -> {
                }));
        assertEquals("task must not be null", nullTask.getMessage());
        NullPointerException nullHandler = assertThrows(NullPointerException.class,
                () -> executor.submit(() -> {
                }, null));
        assertEquals("finallyHandler must not be null", nullHandler.getMessage());
    }

    @Test
    @DisplayName("并发批量提交：全部任务执行且每次提交恰好一次执行完毕收尾")
    void concurrentSubmissionsAllFinishWithExecutedOnce() throws Exception {
        ForkJoinMicroTaskExecutor executor = ForkJoinMicroTaskExecutor.ofShared();
        int total = 32;
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger finallyCount = new AtomicInteger();
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();
        List<Thread> submitters = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            Thread submitter = new Thread(() -> executor.submit(done::countDown, signal -> {
                finishes.add(signal.finallyType());
                finallyCount.incrementAndGet();
            }));
            submitters.add(submitter);
            submitter.start();
        }
        for (Thread submitter : submitters) {
            submitter.join(10_000);
        }
        assertTrue(done.await(10, TimeUnit.SECONDS), "全部任务都应执行完成");
        assertEquals(total, finallyCount.get(), "每次提交恰好一次收尾");
        assertTrue(finishes.stream().allMatch(type -> type == FinallyType.EXECUTED), "共享池不应出现拒绝收尾");
    }

    /** 反射取执行器携带的任务池，供测试模拟池终止。 */
    private static ForkJoinPool poolOf(ForkJoinMicroTaskExecutor executor) {
        try {
            Field field = ForkJoinMicroTaskExecutor.class.getDeclaredField("pool");
            field.setAccessible(true);
            return (ForkJoinPool) field.get(executor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 反射替换执行器携带的任务池，供测试模拟受理阶段抛出 Error 的提交路径。 */
    private static void replacePool(ForkJoinMicroTaskExecutor executor, ForkJoinPool pool) {
        try {
            Field field = ForkJoinMicroTaskExecutor.class.getDeclaredField("pool");
            field.setAccessible(true);
            field.set(executor, pool);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 反射置空共享单例，使后续并发访问复现首次创建路径。 */
    private static void resetSharedForFirstAccess() {
        try {
            Field field = ForkJoinMicroTaskExecutor.class.getDeclaredField("shared");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}