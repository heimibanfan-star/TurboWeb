package top.turboweb.http.server.runtime;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.jupiter.api.Test;
import top.turboweb.common.utils.resource.ScopedResource;
import top.turboweb.common.utils.thread.VirtualThreads;
import top.turboweb.http.server.handler.WebSocketInitChannelHandler;
import top.turboweb.websocket.dispatch.WebSocketDispatcherChannelHandler;
import top.turboweb.websocket.handler.WebSocketHandler;
import top.turboweb.websocket.session.WebSocketSession;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebSocketServerRuntime.Builder} 的校验分支与装配（spec §5 server/runtime 行）。
 *
 * <p>两种线程模型的差异是本 Builder 唯一有观测价值的装配分支：虚拟线程复用全局池、
 * 工作窃取建私有 {@code ForkJoinPool} 并由应用关闭。L1 用反射把私有池取出来直接断言
 * 其 {@code isShutdown()}，不断言任何线程终止。
 */
class WebSocketServerRuntimeTest {

    private static final WebSocketHandler HANDLER = new TestWebSocketHandler();

    @Test
    void pathRejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().path(null));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().path(""));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().path("   "));
    }

    @Test
    void handlerRejectsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime.Builder().handler(null));
        assertEquals("webSocketHandler must not be null", ex.getMessage());
    }

    @Test
    void forkJoinThreadNumRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().forkJoinThreadNum(0));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().forkJoinThreadNum(-1));
    }

    @Test
    void buildWithoutPathIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new WebSocketServerRuntime.Builder().handler(HANDLER).build());
        assertEquals("websocketPath can not be blank", ex.getMessage());
    }

    @Test
    void buildWithoutHandlerIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime.Builder().path("/ws/.*").build());
        assertEquals("webSocketHandler can not be null", ex.getMessage());
    }

    @Test
    void constructorRejectsNullArguments() {
        NullPointerException ex1 = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime(null, new ScopedResource()));
        assertEquals("The websocket init handler must not be null", ex1.getMessage());

        WebSocketInitChannelHandler handler = WebSocketInitChannelHandler.builder()
                .websocketPath("/ws/.*")
                .webSocketHandler(HANDLER)
                .build();
        NullPointerException ex2 = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime(handler, null));
        assertEquals("The resource scope must not be null", ex2.getMessage());
    }

    @Test
    void defaultModelUsesGlobalVirtualThreadPool() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .build();

        assertSame(VirtualThreads.getPool(), dispatcherPool(runtime),
                "未设置线程数时，分发处理器必须复用 JVM 全局虚拟线程池");
        runtime.close();
    }

    @Test
    void forkJoinModelOwnsAndClosesItsPool() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .forkJoinThreadNum(2)
                .build();

        ExecutorService pool = dispatcherPool(runtime);
        assertNotSame(VirtualThreads.getPool(), pool, "工作窃取模式必须是应用私有的池");
        assertFalse(pool.isShutdown(), "构建后私有池尚未关闭");

        runtime.close();

        assertTrue(pool.isShutdown(), "forkJoin 模式下应用关闭必须 shutdown 私有池");
    }

    @Test
    void closeIsIdempotent() {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .build();
        ScopedResource scope = runtime.getScopedResource();
        assertSame(scope, runtime.getScopedResource());

        assertDoesNotThrow(runtime::close);
        assertDoesNotThrow(runtime::close);
    }

    /**
     * 反射取分发处理器私有的执行器：{@code WebSocketServerRuntime} 只暴露 {@code configure}，
     * 线程模型差异只能这样观测。字段改名会让本用例以 {@code NoSuchFieldException} 变红。
     */
    private static ExecutorService dispatcherPool(WebSocketServerRuntime runtime) throws Exception {
        Field handlerField = WebSocketServerRuntime.class.getDeclaredField("webSocketInitHandler");
        handlerField.setAccessible(true);
        Object initHandler = handlerField.get(runtime);

        Field dispatcherField = WebSocketInitChannelHandler.class.getDeclaredField("webSocketDispatcherHandler");
        dispatcherField.setAccessible(true);
        Object dispatcher = dispatcherField.get(initHandler);

        Field poolField = WebSocketDispatcherChannelHandler.class.getDeclaredField("POOL");
        poolField.setAccessible(true);
        return (ExecutorService) poolField.get(dispatcher);
    }

    /** L1 不触发任何回调，实现仅用于满足构造要求。 */
    private static final class TestWebSocketHandler implements WebSocketHandler {

        @Override
        public void onOpen(WebSocketSession session) {
        }

        @Override
        public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
        }

        @Override
        public void onClose(WebSocketSession session) {
        }
    }
}
