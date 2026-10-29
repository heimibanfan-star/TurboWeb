package top.turboweb.websocket;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.ForkJoinMicroTaskExecutor;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;

import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebSocketServerRuntime.Builder} 的校验分支与装配（spec §5 server/runtime 行）。
 *
 * <p>两种消息处理线程模型的差异是本 Builder 唯一有观测价值的装配分支：缺省为非受限虚拟线程
 * 微任务执行器，启用工作窃取后为应用私有的隔离 ForkJoin 微任务执行器并由应用关闭回收。
 * L1 用反射把分发处理器注入的执行器取出来断言其类型，工作窃取模式再取其私有任务池断言
 * {@code isShutdown()}，不断言任何线程终止。
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
    void useForkJoinWithThreadNumRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().useForkJoin(0));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketServerRuntime.Builder().useForkJoin(-1));
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
    void constructorRejectsNullHandler() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime(null, null));
        assertEquals("The websocket init handler must not be null", ex.getMessage());
    }

    @Test
    void constructorRejectsNullExecutor() {
        WebSocketInitChannelHandler handler = WebSocketInitChannelHandler.builder()
                .websocketPath("/ws/.*")
                .webSocketHandler(HANDLER)
                .build();
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new WebSocketServerRuntime(handler, null));
        assertEquals("The micro task executor must not be null", ex.getMessage());
    }

    @Test
    void defaultModelUsesUnlimitedVirtualThreadExecutor() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .build();

        MicroTaskExecutor executor = dispatcherExecutor(runtime);
        assertInstanceOf(VirtualThreadMicroTaskExecutor.class, executor,
                "未启用工作窃取时，分发处理器必须使用虚拟线程微任务执行器");
        assertFalse(((VirtualThreadMicroTaskExecutor) executor).isLimited(),
                "缺省线程模型必须是非受限实现，逐任务直通执行不排队");
        runtime.close();
    }

    @Test
    void forkJoinModelOwnsAndClosesItsPool() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .useForkJoin(2)
                .build();

        MicroTaskExecutor executor = dispatcherExecutor(runtime);
        assertInstanceOf(ForkJoinMicroTaskExecutor.class, executor,
                "启用工作窃取后，分发处理器必须使用ForkJoin微任务执行器");
        ForkJoinPool pool = forkJoinPoolOf(executor);
        assertEquals(2, pool.getParallelism(), "并行度应按给定线程数");
        assertFalse(pool.isShutdown(), "构建后私有池尚未关闭");

        runtime.close();

        assertTrue(pool.isShutdown(), "forkJoin 模式下应用关闭必须 shutdown 私有池");
    }

    @Test
    void forkJoinModelWithoutCountUsesDefaultParallelism() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .useForkJoin()
                .build();

        MicroTaskExecutor executor = dispatcherExecutor(runtime);
        assertInstanceOf(ForkJoinMicroTaskExecutor.class, executor,
                "无参重载同样应启用ForkJoin微任务执行器");
        ForkJoinPool pool = forkJoinPoolOf(executor);
        assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), pool.getParallelism(),
                "无参重载应按默认并行度创建");

        runtime.close();

        assertTrue(pool.isShutdown(), "未指定线程数时应用关闭同样要回收私有任务池");
    }

    @Test
    void configureAppendsInitHandlerToPipeline() {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .build();
        EmbeddedChannel channel = new EmbeddedChannel();

        runtime.configure(channel.pipeline());

        assertNotNull(channel.pipeline().get(WebSocketInitChannelHandler.class),
                "每条新连接的pipeline尾部必须追加初始化处理器");

        runtime.close();
        channel.finishAndReleaseAll();
    }

    @Test
    void closeIsIdempotentAndShutsDownOwnedPool() throws Exception {
        WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(HANDLER)
                .useForkJoin(1)
                .build();
        MicroTaskExecutor executor = dispatcherExecutor(runtime);
        ForkJoinPool pool = forkJoinPoolOf(executor);

        assertDoesNotThrow(runtime::close);
        assertDoesNotThrow(runtime::close);
        assertTrue(pool.isShutdown(), "重复关闭为空操作，私有池应已回收");
    }

    /**
     * 反射取分发处理器注入的微任务执行器：{@code WebSocketServerRuntime} 只暴露 {@code configure}，
     * 线程模型差异只能这样观测。字段改名会让本用例以 {@code NoSuchFieldException} 变红。
     */
    private static MicroTaskExecutor dispatcherExecutor(WebSocketServerRuntime runtime) throws Exception {
        Field handlerField = WebSocketServerRuntime.class.getDeclaredField("webSocketInitHandler");
        handlerField.setAccessible(true);
        Object initHandler = handlerField.get(runtime);

        Field dispatcherField = WebSocketInitChannelHandler.class.getDeclaredField("webSocketDispatcherHandler");
        dispatcherField.setAccessible(true);
        Object dispatcher = dispatcherField.get(initHandler);

        Field executorField = WebSocketChannelHandler.class.getDeclaredField("microTaskExecutor");
        executorField.setAccessible(true);
        return (MicroTaskExecutor) executorField.get(dispatcher);
    }

    /**
     * 反射取 ForkJoin 微任务执行器私有的任务池：关闭与否只能对池本身断言。
     */
    private static ForkJoinPool forkJoinPoolOf(MicroTaskExecutor executor) throws Exception {
        Field poolField = ForkJoinMicroTaskExecutor.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        return (ForkJoinPool) poolField.get(executor);
    }

    /** L1 不触发任何回调，实现仅用于满足构造要求。 */
    private static final class TestWebSocketHandler extends WebSocketHandler {

        @Override
        public void onText(WebSocketSession session, String content) {
        }

        @Override
        public void onBinary(WebSocketSession session, byte[] content) {
        }
    }
}
