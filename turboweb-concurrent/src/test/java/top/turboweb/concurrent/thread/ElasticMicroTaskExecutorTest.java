package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void onFinally(MicroTaskExecutor.FinallySignal finallySignal) {
            calls.incrementAndGet();
            this.signal = finallySignal;
        }

        /** 收尾回调被调用的次数：收尾必达且仅一次契约下必须恒为 1。 */
        int calls() {
            return calls.get();
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
        // 收尾回调在任务计数落地之后异步触发，与计数之间无先后保证，同样须轮询等待
        assertTrue(awaitUntil(catcher::executed, 5_000), "已受理的任务在关闭后仍要以已执行收尾");
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

    @Test
    @DisplayName("关闭宽限期内提交的任务不丢弃：缓冲队列置为关闭前入队的任务由收尾线程执行完毕")
    void taskSubmittedDuringCloseWindowIsStillExecuted() throws Exception {
        // 4 格缓冲 + 1 格核心队列 + 2 条上限线程：三层占满后，任务只能进缓冲或被拒
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        CountDownLatch blockerGate = new CountDownLatch(1);
        // A 卡住搬运线程的就地执行路径：A 开跑即证明核心执行器已关闭且关闭状态尚未置位
        CountDownLatch taskAGate = new CountDownLatch(1);
        CountDownLatch aStarted = new CountDownLatch(1);
        Runnable blocker = () -> {
            try {
                blockerGate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable taskA = () -> {
            aStarted.countDown();
            // 搬运线程就地执行 A 时带着 close 留下的中断标志，首轮 await 会立即被打断；
            // 吞掉中断继续等，才能把"搬运线程已进入退出路径但关闭状态未置位"的窗口撑住
            while (true) {
                try {
                    taskAGate.await();
                    return;
                } catch (InterruptedException e) {
                    // 中断已随异常清位，循环重入等待
                }
            }
        };
        SignalCatcher aCatcher = new SignalCatcher();
        SignalCatcher bCatcher = new SignalCatcher();
        SignalCatcher cCatcher = new SignalCatcher();
        SignalCatcher eCatcher = new SignalCatcher();
        AtomicInteger executed = new AtomicInteger();
        AtomicReference<Throwable> closeError = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try {
                executor.close();
            } catch (Throwable t) {
                closeError.set(t);
            }
        });
        try {
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());
            executor.submit(taskA, aCatcher);
            assertTrue(awaitUntil(() -> cacheSize(executor) == 0, 2_000),
                    "搬运线程必须已取走 A 并卡在退避重试——这是缓冲队列腾空的观测点");
            executor.submit(executed::incrementAndGet, bCatcher);
            executor.submit(executed::incrementAndGet, cCatcher);

            closer.start();
            assertTrue(aStarted.await(5, TimeUnit.SECONDS),
                    "A 必须在关闭序列中被就地执行，否则本用例的窗口构造不成立");
            // 此刻关闭已开始但缓冲队列尚未置为关闭：新提交必须照常受理，不许丢弃
            executor.submit(executed::incrementAndGet, eCatcher);

            taskAGate.countDown();
            closer.join(10_000);
            assertFalse(closer.isAlive(), "close 必须等缓冲队列执行完毕后才返回");
            assertNull(closeError.get(), "close 不得抛出异常");
            assertTrue(awaitUntil(() -> executed.get() == 3, 5_000),
                    () -> "宽限期内提交的 E 与缓冲中的 B、C 都必须被执行；实际 " + executed.get() + " 个");
            assertTrue(aCatcher.executed(), "就地执行的 A 同样以已执行收尾");
            assertTrue(eCatcher.executed(), "宽限期内提交的 E 必须被执行并以已执行收尾");
            assertEquals(1, aCatcher.calls(), "A 的收尾回调必须恰好一次");
            assertEquals(1, eCatcher.calls(), "E 的收尾回调必须恰好一次");
        } finally {
            taskAGate.countDown();
            blockerGate.countDown();
            executor.close();
        }
    }

    @Test
    @DisplayName("缓冲任务抛 Error 不得中断排空也不得从 close 冒出，其后的任务仍须执行")
    void errorFromCachedTaskDoesNotAbortCloseDrain() throws Exception {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        CountDownLatch blockerGate = new CountDownLatch(1);
        Runnable blocker = () -> {
            try {
                blockerGate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        SignalCatcher errorCatcher = new SignalCatcher();
        SignalCatcher afterCatcher = new SignalCatcher();
        AtomicInteger after = new AtomicInteger();
        try {
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());
            executor.submit(blocker, new SignalCatcher());
            executor.submit(() -> {
                throw new AssertionError("cached task boom");
            }, errorCatcher);
            assertTrue(awaitUntil(() -> cacheSize(executor) == 0, 2_000),
                    "搬运线程必须已取走抛 Error 的任务并卡在退避重试");
            executor.submit(after::incrementAndGet, afterCatcher);

            assertDoesNotThrow(executor::close, "单个任务抛出的 Error 不得从 close 冒出");
            assertTrue(awaitUntil(() -> after.get() == 1, 5_000), "Error 之后的任务仍须被执行");
            assertTrue(errorCatcher.executed(), "抛 Error 的任务同样以已执行收尾");
            assertEquals(1, errorCatcher.calls(), "收尾回调必须恰好一次");
            assertTrue(afterCatcher.executed(), "其后的任务以已执行收尾");
        } finally {
            blockerGate.countDown();
            executor.close();
        }
    }

    @Test
    @DisplayName("收尾回调自身抛异常：工作线程不死亡重建，后续任务仍在同一承载线程执行")
    void callbackFailureDoesNotKillWorkerThread() throws Exception {
        ElasticMicroTaskExecutor executor = new ElasticMicroTaskExecutor(4, 1, 2);
        try {
            AtomicReference<String> firstWorker = new AtomicReference<>();
            AtomicReference<String> secondWorker = new AtomicReference<>();
            CountDownLatch firstDone = new CountDownLatch(1);
            CountDownLatch secondDone = new CountDownLatch(1);
            AtomicInteger callbackCalls = new AtomicInteger();

            executor.submit(() -> {
                firstWorker.set(Thread.currentThread().getName());
                firstDone.countDown();
            }, signal -> {
                callbackCalls.incrementAndGet();
                throw new IllegalStateException("callback boom");
            });
            assertTrue(firstDone.await(5, TimeUnit.SECONDS), "第一个任务应被执行");

            executor.submit(() -> {
                secondWorker.set(Thread.currentThread().getName());
                secondDone.countDown();
            }, new SignalCatcher());
            assertTrue(secondDone.await(5, TimeUnit.SECONDS), "回调抛异常后的后续任务仍应被执行");

            assertEquals(1, callbackCalls.get(), "收尾回调必须恰好一次");
            assertEquals(firstWorker.get(), secondWorker.get(),
                    "回调异常不得杀死承载工作线程：后续任务应在同一工作线程执行");
        } finally {
            executor.close();
        }
    }
}
