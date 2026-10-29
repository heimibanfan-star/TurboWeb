package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹性微任务执行器：参数校验、提交收尾语义、未受理收尾、关闭不丢任务。
 */
class ElasticMicroTaskExecutorTest {

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

    /** 读私有缓冲队列长度，只为把"搬运线程已取走手上任务"变成可断言的观测点。 */
    private static int cacheSize(ElasticMicroTaskExecutor executor) {
        try {
            Field f = ElasticMicroTaskExecutor.class.getDeclaredField("cacheQueue");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            BlockingQueue<Runnable> queue = (BlockingQueue<Runnable>) f.get(executor);
            return queue.size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("读不到 cacheQueue，本类的背压用例无法取证", e);
        }
    }

    /** 收集一次提交的收尾信号：既有收尾必达契约下，信号为 null 即收尾未发生。 */
    private static final class SignalCatcher implements MicroTaskExecutor.FinallyHandler {

        private volatile MicroTaskExecutor.FinallySignal signal;

        @Override
        public void onFinally(MicroTaskExecutor.FinallySignal finallySignal) {
            this.signal = finallySignal;
        }

        boolean executed() {
            MicroTaskExecutor.FinallySignal s = signal;
            return s != null && s.finallyType() == MicroTaskExecutor.FinallyType.EXECUTED;
        }

        boolean notAccepted() {
            MicroTaskExecutor.FinallySignal s = signal;
            return s != null && s.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT;
        }
    }

    @Test
    void nonPositiveArgumentsAreRejected() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticMicroTaskExecutor(0, 1, 1));
        assertEquals("cacheQueue must be greater than 0", e1.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticMicroTaskExecutor(1, 0, 1));
        assertEquals("coreQueue must be greater than 0", e2.getMessage());
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class,
                () -> new ElasticMicroTaskExecutor(1, 1, 0));
        assertEquals("maxThreadNum must be greater than 0", e3.getMessage());
    }

    @Test
    void nullTaskOrHandlerThrowsNpeBeforeAnyQueueing() {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        try {
            SignalCatcher catcher = new SignalCatcher();
            NullPointerException e1 = assertThrows(NullPointerException.class,
                    () -> executor.submit(null, catcher));
            assertEquals("task must not be null", e1.getMessage());
            NullPointerException e2 = assertThrows(NullPointerException.class,
                    () -> executor.submit(() -> { }, null));
            assertEquals("finallyHandler must not be null", e2.getMessage());
            assertFalse(catcher.notAccepted(), "参数校验先于任何资源动作，不得触发收尾回调");
        } finally {
            executor.close();
        }
    }

    @Test
    void submittedTaskRunsAndReportsExecutedOnWorkerThread() throws Exception {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        CountDownLatch ran = new CountDownLatch(1);
        try {
            SignalCatcher catcher = new SignalCatcher();
            executor.submit(ran::countDown, catcher);
            assertTrue(ran.await(5, TimeUnit.SECONDS), "任务必须在 5s 内被执行");
            // 收尾回调在任务执行完之后才触发，与倒计数之间无先后保证，须轮询等待
            assertTrue(awaitUntil(catcher::executed, 5_000), "受理的任务执行完毕必须以已执行收尾");
            assertEquals(MicroTaskExecutor.FinallySignal.CallbackThread.WORKER,
                    catcher.signal.callbackThread(), "执行收尾必须发生在承载任务的工作线程方");
        } finally {
            executor.close();
        }
    }

    @Test
    void submitAfterCloseIsNotAcceptedWithoutThrowing() {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        executor.close();
        AtomicInteger ran = new AtomicInteger();
        SignalCatcher catcher = new SignalCatcher();
        executor.submit(ran::incrementAndGet, catcher);
        assertTrue(catcher.notAccepted(), "关闭后提交必须以未受理收尾");
        assertEquals(MicroTaskExecutor.FinallySignal.CallbackThread.SUBMITTER,
                catcher.signal.callbackThread(), "未受理收尾必须发生在提交线程方");
        assertEquals(0, ran.get(), "未受理的任务不得执行");
    }

    @Test
    @DisplayName("try-with-resources 走 close()，已受理任务跑完且重复关闭幂等")
    void closeRunsAcceptedTasksAndIsIdempotent() throws Exception {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        AtomicInteger executed = new AtomicInteger();
        SignalCatcher catcher = new SignalCatcher();
        try (executor) {
            executor.submit(executed::incrementAndGet, catcher);
        }
        // close() 只保证"不再接新任务"，核心队列里那个任务由 worker 线程异步跑，
        // close() 返回时它可能还没执行——直接断言计数是抖的，须等它落地
        assertTrue(awaitUntil(() -> executed.get() == 1, 5_000), "5s 内必须跑完已受理的任务");
        assertTrue(catcher.executed(), "已受理的任务在关闭后仍要以已执行收尾");
        executor.close();
        assertEquals(1, executed.get(), "重复 close() 必须幂等，不得重复执行或丢任务");
    }

    @Test
    @DisplayName("钉住 F7：搬运线程抱着的任务也不许丢，close 后已受理数 == 已执行数")
    void closeRunsEveryAcceptedTaskUnderBackpressure() throws Exception {
        // 钉住 F7：若将来修正，本用例应改为期望正确行为
        // 2 条上限线程 + 1 格核心队列 + 2 格缓冲：三层全占满后才开始取证
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(2, 1, 2);
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
            // 占满 worker1、核心队列、worker2：此后任何提交都只会进缓冲或被拒
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());

            SignalCatcher queued = new SignalCatcher();
            executor.submit(executed::incrementAndGet, queued);
            assertTrue(awaitUntil(() -> cacheSize(executor) == 0, 2_000),
                    "搬运线程必须已把它取走并卡在退避重试里——这是缓冲队列腾空的观测点");
            executor.submit(executed::incrementAndGet, new SignalCatcher());
            executor.submit(executed::incrementAndGet, new SignalCatcher());
            SignalCatcher rejected = new SignalCatcher();
            executor.submit(executed::incrementAndGet, rejected);
            assertTrue(rejected.notAccepted(), "核心队列与缓冲队列皆满时必须以未受理收尾，不许抛异常");
            assertEquals(MicroTaskExecutor.FinallySignal.CallbackThread.SUBMITTER,
                    rejected.signal.callbackThread(), "未受理收尾必须发生在提交线程方");
            assertEquals(0, executed.get(), "被拒的任务不得执行");

            blockerGate.countDown();
            executor.close();
            assertTrue(awaitUntil(() -> executed.get() == 3, 5_000), () -> "3 个被受理的任务全部执行；实际 "
                    + executed.get() + " 个——若少一个，说明搬运线程手上那个任务在 close 序列里滞留缓冲队列");
            assertTrue(queued.executed(), "进过缓冲队列的任务执行后同样以已执行收尾");
        } finally {
            blockerGate.countDown();
            executor.close();
        }
    }
}
