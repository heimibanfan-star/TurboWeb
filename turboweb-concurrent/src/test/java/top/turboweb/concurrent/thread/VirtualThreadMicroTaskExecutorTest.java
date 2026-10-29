package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallyHandler;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallyType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link VirtualThreadMicroTaskExecutor} 基类的启动失败收尾契约：补偿动作先于收尾回调执行，
 * 收尾以未受理情形触发，原始异常原样抛出；收尾回调自身抛出的异常由安全调用点就地记账。
 */
class VirtualThreadMicroTaskExecutorTest {

    /** 以固定启动失败模拟线程创建失败，其余走基类的启动收尾逻辑。 */
    private static final class StartFailingResource extends VirtualThreadMicroTaskExecutor {

        private final Runnable compensation;

        StartFailingResource(Runnable compensation) {
            super("vtr-start-failure");
            this.compensation = compensation;
        }

        @Override
        public Thread run(Runnable task, FinallyHandler finallyHandler) {
            requireArguments(task, finallyHandler);
            return startThread(task, compensation, finallyHandler);
        }

        @Override
        protected Thread startThread(Runnable body) {
            throw new IllegalStateException("start failed");
        }
    }

    /** 记录 submit 传入参数、丢弃返回值的委托桩：run 原样记录任务与收尾回调并返回 null。 */
    private static final class RecordingResource extends VirtualThreadMicroTaskExecutor {

        Runnable recordedTask;
        FinallyHandler recordedHandler;

        RecordingResource() {
            super("vtr-submit");
        }

        @Override
        public Thread run(Runnable task, FinallyHandler finallyHandler) {
            this.recordedTask = task;
            this.recordedHandler = finallyHandler;
            return null;
        }
    }

    @Test
    @DisplayName("线程未能启动：补偿动作先执行，再以未受理情形收尾，原始异常原样抛出")
    void startFailureCompensatesThenFinishesWithNotAccepted() {
        List<String> events = new CopyOnWriteArrayList<>();
        StartFailingResource resource = new StartFailingResource(() -> events.add("compensate"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resource.run(() -> fail("任务不应执行"),
                        signal -> events.add(signal.finallyType().name())));

        assertEquals("start failed", failure.getMessage(), "原始异常原样抛出");
        assertEquals(List.of("compensate", FinallyType.NOT_ACCEPT.name()), events,
                "先归还启动前占用的资源，再以未受理情形收尾");
        assertEquals(0, failure.getSuppressed().length, "正常路径不产生被抑制异常");
    }

    @Test
    @DisplayName("线程未能启动且收尾回调抛出：回调异常就地记账，原始异常原样抛出，回调恰好一次")
    void startFailureKeepsOriginalExceptionWhenFinallyThrows() {
        StartFailingResource resource = new StartFailingResource(() -> {
        });
        AtomicInteger calls = new AtomicInteger();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resource.run(() -> fail("任务不应执行"), type -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("finally boom");
                }));

        assertEquals("start failed", failure.getMessage(), "收尾回调抛出不影响原始异常抛出");
        assertEquals(0, failure.getSuppressed().length, "回调异常由安全调用点就地记账，不再挂为抑制异常");
        assertEquals(1, calls.get(), "收尾回调必须恰好一次");
    }

    @Test
    @DisplayName("submit 委托 run：任务与收尾回调原样传递，返回值丢弃")
    void submitDelegatesToRunAndDiscardsReturnedThread() {
        RecordingResource resource = new RecordingResource();
        Runnable task = () -> {
        };
        FinallyHandler handler = type -> {
        };

        resource.submit(task, handler);

        assertSame(task, resource.recordedTask, "任务原样传给 run");
        assertSame(handler, resource.recordedHandler, "收尾回调原样传给 run");
    }
}
