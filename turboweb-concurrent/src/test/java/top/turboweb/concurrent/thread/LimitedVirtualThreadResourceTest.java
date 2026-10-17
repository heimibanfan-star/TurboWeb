package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import top.turboweb.concurrent.thread.VirtualThreadResource.FinallyHandler;
import top.turboweb.concurrent.thread.VirtualThreadResource.FinallyType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static top.turboweb.concurrent.thread.VirtualThreadResource.FinallyType.EXECUTED;
import static top.turboweb.concurrent.thread.VirtualThreadResource.FinallyType.REJECTED;

/**
 * {@link LimitedVirtualThreadResource} 的构造校验、资源获取、排队挂起与双线程位拒绝收尾。
 *
 * <p>并发语义的取证口径：挂起额度以 CAS 在提交侧条件递增，超限直接拒绝且计数不上探，
 * 因此 run 返回后即可直接断言计数；等待结果一律用带超时的闩或轮询等待收敛，
 * 收尾回调记录发生线程与收尾情形；占用资源的任务以门闩释放，保证"资源被占住"这一前提对每个用例成立。
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class LimitedVirtualThreadResourceTest {

    /** 轮询间隔：足够小以捕捉毫秒级状态翻转，足够大以免空转。 */
    private static final long POLL_INTERVAL_MILLIS = 5L;

    /**
     * 把"等到条件成立"写成有截止时间的轮询：条件一成立立刻返回 true；
     * 返回 false 表示整个窗口内条件始终不成立——这正是负向断言需要的证据。
     */
    private static boolean awaitUntil(BooleanSupplier condition, long deadlineMillis) {
        long deadline = System.nanoTime() + deadlineMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        // 出界前最后一次确认，避免"恰好在 deadline 上成立"被判成没成立
        return condition.getAsBoolean();
    }

    /** 收尾回调记录器：记录收尾情形与回调发生线程，并放行等待中的用例线程。 */
    private static final class RejectionRecord {
        final CountDownLatch rejected = new CountDownLatch(1);
        volatile FinallyType finallyType;
        volatile Thread callbackThread;

        FinallyHandler handler() {
            return this::accept;
        }

        void accept(FinallyType type) {
            finallyType = type;
            callbackThread = Thread.currentThread();
            rejected.countDown();
        }

        /** 当前线程同步拒绝的取证：run 返回即应已回调，不允许还需要再等。 */
        FinallyType requireSyncRejected() {
            assertEquals(0, rejected.getCount(), "当前线程拒绝应在 run 返回前完成");
            return finallyType;
        }
    }

    /** 不应发生的拒绝：收尾情形为 REJECTED 直接判失败；执行完毕情形放行。 */
    private static FinallyHandler neverRejected() {
        return type -> {
            if (type == REJECTED) {
                fail("任务不应被拒绝");
            }
        };
    }

    /** 占住资源的阻塞任务：started 证明已运行并占住，gate 释放后结束。 */
    private static final class BlockedHolder implements Runnable {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);

        @Override
        public void run() {
            started.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 观测任务夹具：记录是否执行、执行线程名，可选写入执行序列。 */
    private static final class ObservedTask implements Runnable {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> threadName = new AtomicReference<>();
        final AtomicBoolean ran = new AtomicBoolean();
        private final String mark;
        private final List<String> marks;

        ObservedTask(String mark, List<String> marks) {
            this.mark = mark;
            this.marks = marks;
        }

        @Override
        public void run() {
            ran.set(true);
            threadName.set(Thread.currentThread().getName());
            if (mark != null) {
                marks.add(mark);
            }
            done.countDown();
        }
    }

    @Test
    @DisplayName("构造参数校验：资源数须大于0，挂起数与挂起时间不得为负，线程名不得为null；公平标志与初始读数如实暴露")
    void constructorValidatesArgumentsAndExposesInitialState() {
        IllegalArgumentException resourceCountError =
                assertThrows(IllegalArgumentException.class, () -> new LimitedVirtualThreadResource(0, 1, 1, "n", false));
        assertEquals("资源数必须大于0", resourceCountError.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new LimitedVirtualThreadResource(-1, 1, 1, "n", false));
        assertThrows(IllegalArgumentException.class, () -> new LimitedVirtualThreadResource(1, -1, 1, "n", false));
        assertThrows(IllegalArgumentException.class, () -> new LimitedVirtualThreadResource(1, 1, -1, "n", false));
        assertThrows(NullPointerException.class, () -> new LimitedVirtualThreadResource(1, 1, 1, null, false));

        LimitedVirtualThreadResource fair = new LimitedVirtualThreadResource(3, 0, 0, "n", true);
        assertTrue(fair.isFair(), "公平标志应如实反映构造参数");
        assertEquals(3, fair.availableResources(), "初始空闲资源数应等于资源数");
        assertEquals(0, fair.suspendedThreads(), "初始挂起数应为0");
        assertFalse(new LimitedVirtualThreadResource(1, 0, 0, "n", false).isFair());
    }

    @Test
    @DisplayName("run 的 null 参数先行拒绝，不消耗资源")
    void runRejectsNullTaskAndHandler() {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 1, 10, "n", true);

        assertThrows(NullPointerException.class, () -> resource.run(null, neverRejected()));
        assertThrows(NullPointerException.class, () -> resource.run(() -> {
        }, null));
        assertEquals(1, resource.availableResources(), "参数校验先于资源获取");
    }

    @Test
    @DisplayName("资源空闲：任务在命名虚拟线程执行，资源执行后归还，全程不触发拒绝")
    void availableResourceRunsTaskOnNamedVirtualThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(2, 1, 1_000, "vtr-worker", true);
        ObservedTask task = new ObservedTask(null, null);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread worker = resource.run(task, finishes::add);

        assertNotNull(worker, "接受提交应返回承载线程");
        assertTrue(worker.isVirtual(), "承载线程应为虚拟线程");
        assertTrue(task.done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertEquals("vtr-worker", task.threadName.get(), "任务应运行在配置名称的线程上");
        worker.join(5_000);
        assertEquals(List.of(EXECUTED), finishes, "任务结束后以执行完毕情形收尾一次");
        assertEquals(2, resource.availableResources(), "执行后资源应全部归还");
        assertEquals(0, resource.suspendedThreads());
    }

    @Test
    @DisplayName("任务抛出异常资源照常释放")
    void taskFailureStillReleasesResource() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 0, 0, "vtr-fail", false);
        List<FinallyType> finishes = new CopyOnWriteArrayList<>();

        Thread worker = resource.run(() -> {
            throw new IllegalStateException("boom");
        }, finishes::add);

        assertNotNull(worker);
        worker.join(5_000);
        assertFalse(worker.isAlive());
        assertEquals(List.of(EXECUTED), finishes, "任务抛出异常后仍以执行完毕情形收尾");
        assertEquals(1, resource.availableResources(), "任务抛出异常后资源照常释放");
        assertEquals(0, resource.suspendedThreads());
    }

    @Test
    @DisplayName("挂起额度已满：超限提交直接拒绝、计数不上探，在当前线程同步收到拒绝收尾并返回 null")
    void queueFullRejectsSynchronouslyOnCurrentThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 1, 5_000, "vtr-queue", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertNotNull(holderThread);
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));
        assertEquals(0, resource.availableResources(), "唯一许可由占用者持有");

        ObservedTask waiter1Task = new ObservedTask(null, null);
        Thread waiter1 = resource.run(waiter1Task, neverRejected());
        assertNotNull(waiter1, "第一个等待者应获得挂起额度");
        assertEquals(1, resource.suspendedThreads(), "额度登记发生在提交侧");

        RejectionRecord record = new RejectionRecord();
        ObservedTask waiter2Task = new ObservedTask(null, null);
        Thread waiter2 = resource.run(waiter2Task, record.handler());

        assertNull(waiter2, "额度已满不应创建线程");
        assertEquals(REJECTED, record.requireSyncRejected());
        assertSame(Thread.currentThread(), record.callbackThread, "同步拒绝回调应发生在当前线程");
        assertFalse(waiter2Task.ran.get());
        assertEquals(1, resource.suspendedThreads(), "CAS 超限判定失败不产生写入，计数不上探也不泄漏");

        holder.gate.countDown();
        assertTrue(waiter1Task.done.await(5, TimeUnit.SECONDS), "释放后等待者应取得资源并执行");
        waiter1.join(5_000);
        holderThread.join(5_000);
        assertTrue(awaitUntil(() -> resource.availableResources() == 1, 2_000), "资源最终全部归还");
        assertEquals(0, resource.suspendedThreads());
    }

    @Test
    @DisplayName("最大挂起时间为零：资源不足不登记额度、不建线程，当前线程同步拒绝")
    void zeroTimeoutRejectsSynchronouslyOnCurrentThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 5, 0, "vtr-zerotimeout", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        RejectionRecord record = new RejectionRecord();
        ObservedTask task = new ObservedTask(null, null);
        Thread worker = resource.run(task, record.handler());

        assertNull(worker);
        assertEquals(REJECTED, record.requireSyncRejected());
        assertSame(Thread.currentThread(), record.callbackThread);
        assertFalse(task.ran.get());
        assertEquals(0, resource.suspendedThreads(), "零预算拒绝不应登记挂起额度");

        holder.gate.countDown();
        holderThread.join(5_000);
    }

    @Test
    @DisplayName("最大挂起数为零：资源耗尽即刻在当前线程拒绝，等待预算再大也不排队")
    void zeroMaxSuspendRejectsSynchronouslyOnCurrentThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 0, 1_000, "vtr-zeromax", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        RejectionRecord record = new RejectionRecord();
        ObservedTask task = new ObservedTask(null, null);
        Thread worker = resource.run(task, record.handler());

        assertNull(worker);
        assertEquals(REJECTED, record.requireSyncRejected());
        assertSame(Thread.currentThread(), record.callbackThread);
        assertFalse(task.ran.get());
        assertEquals(0, resource.suspendedThreads());

        holder.gate.countDown();
        holderThread.join(5_000);
    }

    @Test
    @DisplayName("公平模式：两名等待者按提交先后取得资源执行，均不被拒绝，额度与资源全部归还")
    void waitersAcquireInOrderWhenFair() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 5, 5_000, "vtr-fair", true);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        List<String> order = new CopyOnWriteArrayList<>();
        ObservedTask taskA = new ObservedTask("A", order);
        Thread waiterA = resource.run(taskA, neverRejected());
        assertNotNull(waiterA);
        // 拉开两个等待者进入等待队列的间隔，让"先来"在队列次序上可证
        Thread.sleep(150);
        ObservedTask taskB = new ObservedTask("B", order);
        Thread waiterB = resource.run(taskB, neverRejected());
        assertNotNull(waiterB);
        assertEquals(2, resource.suspendedThreads());

        holder.gate.countDown();
        assertTrue(taskA.done.await(5, TimeUnit.SECONDS));
        assertTrue(taskB.done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("A", "B"), order, "公平模式应按提交先后交付资源");
        assertEquals("vtr-fair", taskA.threadName.get());
        assertEquals("vtr-fair", taskB.threadName.get());
        waiterA.join(5_000);
        waiterB.join(5_000);
        holderThread.join(5_000);
        assertTrue(awaitUntil(() -> resource.availableResources() == 1, 2_000));
        assertEquals(0, resource.suspendedThreads());
    }

    @Test
    @DisplayName("等待预算耗尽：等待线程以拒绝情形收尾，额度先归还、任务未执行、资源仍被占用")
    void timedWaitBudgetExhaustedRejectsOnNewThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 5, 50, "vtr-reject", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        RejectionRecord record = new RejectionRecord();
        ObservedTask task = new ObservedTask(null, null);
        Thread waiter = resource.run(task, record.handler());
        assertNotNull(waiter);
        assertEquals(1, resource.suspendedThreads());

        assertTrue(record.rejected.await(5, TimeUnit.SECONDS), "限时等待耗尽应触发拒绝");
        assertEquals(REJECTED, record.finallyType);
        assertNotSame(Thread.currentThread(), record.callbackThread, "超时拒绝应发生在等待线程自身");
        assertEquals("vtr-reject", record.callbackThread.getName());
        assertFalse(task.ran.get(), "被拒任务不得执行");
        assertEquals(0, resource.suspendedThreads(), "超时拒绝回调前额度已归还");
        assertEquals(0, resource.availableResources(), "资源仍由占用者持有");

        waiter.join(5_000);
        holder.gate.countDown();
        holderThread.join(5_000);
        assertTrue(awaitUntil(() -> resource.availableResources() == 1, 2_000));
    }

    @Test
    @DisplayName("等待中被中断：放弃等待并在等待线程以拒绝情形收尾，额度归还、任务未执行")
    void interruptedWaiterRejectsOnNewThread() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 5, 10_000, "vtr-interrupt", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        RejectionRecord record = new RejectionRecord();
        ObservedTask task = new ObservedTask(null, null);
        Thread waiter = resource.run(task, record.handler());
        assertNotNull(waiter);
        waiter.interrupt();

        assertTrue(record.rejected.await(5, TimeUnit.SECONDS), "中断应触发拒绝回调");
        assertEquals(REJECTED, record.finallyType);
        assertSame(waiter, record.callbackThread, "中断拒绝应发生在等待线程自身");
        assertFalse(task.ran.get());
        assertEquals(0, resource.suspendedThreads(), "中断归还额度后才回调");

        waiter.join(5_000);
        holder.gate.countDown();
        holderThread.join(5_000);
        assertTrue(awaitUntil(() -> resource.availableResources() == 1, 2_000));
    }

    @Test
    @DisplayName("并发提交下同时在执行的任务数不超过资源数，且全部完成、计数全部归还")
    void concurrentTasksNeverExceedResourceCount() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(3, 50, 3_000, "vtr-stress", true);
        int total = 20;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(total);

        List<Thread> workers = new CopyOnWriteArrayList<>();
        for (int i = 0; i < total; i++) {
            Thread worker = resource.run(() -> {
                int now = active.incrementAndGet();
                maxActive.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                active.decrementAndGet();
                done.countDown();
            }, neverRejected());
            assertNotNull(worker, "挂起额度充足时不应有提交被拒");
            workers.add(worker);
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "全部任务都应完成");
        assertTrue(maxActive.get() <= 3, "同时在执行的任务数不得超过资源数，实测 " + maxActive.get());
        for (Thread worker : workers) {
            worker.join(5_000);
        }
        assertTrue(awaitUntil(() -> resource.availableResources() == 3, 2_000), "资源应全部归还");
        assertEquals(0, resource.suspendedThreads(), "挂起额度应全部归还");
    }

    @Test
    @DisplayName("并发争抢挂起额度：许可全程被占，挂起读数不超上限，全部提交都被拒绝且计数无泄漏")
    void concurrentSuspensionNeverExceedsMaxSuspend() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 4, 300, "vtr-cas", false);
        BlockedHolder holder = new BlockedHolder();
        Thread holderThread = resource.run(holder, neverRejected());
        assertTrue(holder.started.await(5, TimeUnit.SECONDS));

        int total = 16;
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger highestSuspended = new AtomicInteger();
        AtomicInteger newThreadRejections = new AtomicInteger();
        AtomicInteger currentThreadRejections = new AtomicInteger();
        List<Thread> submitters = new CopyOnWriteArrayList<>();
        for (int i = 0; i < total; i++) {
            Thread submitter = Thread.ofVirtual().name("vtr-cas-submit").start(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Thread submitterThread = Thread.currentThread();
                Thread waiter = resource.run(() -> {
                }, type -> {
                    if (type != REJECTED) {
                        return;
                    }
                    if (submitterThread == Thread.currentThread()) {
                        currentThreadRejections.incrementAndGet();
                    } else {
                        newThreadRejections.incrementAndGet();
                    }
                });
                if (waiter == null) {
                    return;
                }
                highestSuspended.accumulateAndGet(resource.suspendedThreads(), Math::max);
                try {
                    waiter.join(6_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            submitters.add(submitter);
        }

        startGate.countDown();
        for (Thread submitter : submitters) {
            submitter.join(10_000);
        }
        assertEquals(total, newThreadRejections.get() + currentThreadRejections.get(), "每个提交都应收到且只收到一次拒绝");
        assertTrue(newThreadRejections.get() >= 4, "至少四枚提交取得额度排队后超时拒绝，实测 " + newThreadRejections.get());
        assertTrue(highestSuspended.get() <= 4, "挂起读数不得超过上限，实测 " + highestSuspended.get());
        assertEquals(0, resource.suspendedThreads(), "全部等待线程退出后额度应无泄漏");

        holder.gate.countDown();
        holderThread.join(5_000);
        assertTrue(awaitUntil(() -> resource.availableResources() == 1, 2_000), "许可应回到占用者释放后的满额");
    }

    @Test
    @DisplayName("收尾情形枚举：三枚取值，values 与 valueOf 往返一致")
    void finallyTypeEnumIsThreeValuedAndRoundTrips() {
        assertEquals(3, FinallyType.values().length);
        assertSame(EXECUTED, FinallyType.valueOf("EXECUTED"));
        assertSame(REJECTED, FinallyType.valueOf("REJECTED"));
        assertSame(FinallyType.NOT_STARTED, FinallyType.valueOf("NOT_STARTED"));
    }
}
