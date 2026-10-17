package top.turboweb.concurrent.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.VirtualThreadResource.FinallyHandler;
import top.turboweb.concurrent.thread.VirtualThreadResource.FinallyType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link VirtualThreadResource} 基类的启动失败收尾契约：补偿动作先于收尾回调执行，
 * 收尾以未启动情形触发，原始异常原样抛出；收尾回调自身抛出的异常作为被抑制异常保留。
 */
class VirtualThreadResourceTest {

    /** 以固定启动失败模拟线程创建失败，其余走基类的启动收尾逻辑。 */
    private static final class StartFailingResource extends VirtualThreadResource {

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

    @Test
    @DisplayName("线程未能启动：补偿动作先执行，再以未启动情形收尾，原始异常原样抛出")
    void startFailureCompensatesThenFinishesWithNotStarted() {
        List<String> events = new CopyOnWriteArrayList<>();
        StartFailingResource resource = new StartFailingResource(() -> events.add("compensate"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resource.run(() -> fail("任务不应执行"),
                        type -> events.add(type.name())));

        assertEquals("start failed", failure.getMessage(), "原始异常原样抛出");
        assertEquals(List.of("compensate", FinallyType.NOT_STARTED.name()), events,
                "先归还启动前占用的资源，再以未启动情形收尾");
        assertEquals(0, failure.getSuppressed().length, "正常路径不产生被抑制异常");
    }

    @Test
    @DisplayName("线程未能启动且收尾回调抛出：回调异常作为被抑制异常挂在原始异常上")
    void startFailureKeepsOriginalExceptionWhenFinallyThrows() {
        StartFailingResource resource = new StartFailingResource(() -> {
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resource.run(() -> fail("任务不应执行"), type -> {
                    throw new IllegalArgumentException("finally boom");
                }));

        assertEquals("start failed", failure.getMessage(), "收尾回调抛出不影响原始异常抛出");
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("finally boom", failure.getSuppressed()[0].getMessage());
    }
}
