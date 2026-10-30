package top.turboweb.http.server.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.connection.SequencedRequest;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.orchestrator.RequestOrchestrator;
import top.turboweb.http.orchestrator.ResultConverterManager;
import top.turboweb.http.orchestrator.parser.HttpContentParserManager;
import top.turboweb.http.orchestrator.writer.ResponseWriterManager;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.http.session.BackHoleSessionManager;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.server.runtime.ResourceManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpRequestSchedulerChannelHandler} 的调度行为锚：
 * 只接收封章后的 {@link SequencedRequest}，以封装携带的序列号建立一请求一连接，
 * 编排原始请求并在返回后归还收到的请求引用；未封章的裸请求不经编排原样透传，
 * 由下游处理器决定。
 * <p>编排器为九组件齐备的真实实例，微任务执行器同步直跑使编排可断言。
 */
class HttpRequestSchedulerChannelHandlerTest {

    /** 捕获编排上下文的中间件：编排是否发生与连接序列号都经上下文观察 */
    private static final class ContextCapturingMiddleware extends Middleware {

        private final List<HttpContext> contexts;

        private ContextCapturingMiddleware(List<HttpContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            contexts.add(ctx);
            return "ok";
        }
    }

    /** 装配九组件齐备的真实编排器：同步微任务执行器、捕获上下文的中间件 */
    private static RequestOrchestrator orchestrator(List<HttpContext> contexts) {
        return RequestOrchestrator.builder()
                .middleware(new ContextCapturingMiddleware(contexts))
                .resourceManager(testResourceManager(Stubs.directExecutor()))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())
                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(new HttpContentParserManager(StandardCharsets.UTF_8, List.of()))
                .build();
    }

    /** 测试资源管理器：内置空间登记同步微任务执行器与桩磁盘IO执行器 */
    private static ResourceManager testResourceManager(MicroTaskExecutor executor) {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.MICRO_TASK_EXECUTOR, executor);
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, Stubs.noopExecutor());
        return new ResourceManager.Builder().build(builtins);
    }

    /** 测试资源管理器：内置空间只登记桩磁盘IO执行器，供响应写出器管理器按名获取 */
    private static ResourceManager writerResourceManager() {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, Stubs.noopExecutor());
        return new ResourceManager.Builder().build(builtins);
    }

    private static FullHttpRequest getRequest(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    @Test
    void sequencedRequestIsOrchestratedWithSequenceCarryingConnection() {
        List<HttpContext> contexts = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(
                HttpRequestSchedulerChannelHandler.builder().orchestrator(orchestrator(contexts)).build());
        FullHttpRequest request = getRequest("/a");
        assertEquals(1, request.refCnt());

        channel.writeInbound(new SequencedRequest(5, request));

        assertEquals(1, contexts.size(), "封章请求进入编排");
        InternalHttpConnection connection = assertInstanceOf(InternalHttpConnection.class,
                contexts.get(0).getConnectSession());
        assertEquals(5, connection.sequence(), "连接携带封装分配的序列号");
        assertSame(channel, connection.getChannel());
        assertEquals(0, request.refCnt(), "调度器返回后归还收到的请求引用");
        channel.finishAndReleaseAll();
    }

    @Test
    void eachSequencedRequestGetsItsOwnConnection() {
        List<HttpContext> contexts = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(
                HttpRequestSchedulerChannelHandler.builder().orchestrator(orchestrator(contexts)).build());
        FullHttpRequest first = getRequest("/1");
        FullHttpRequest second = getRequest("/2");

        channel.writeInbound(new SequencedRequest(1, first));
        channel.writeInbound(new SequencedRequest(2, second));

        assertEquals(2, contexts.size(), "两个请求各自编排");
        InternalHttpConnection firstConnection = assertInstanceOf(InternalHttpConnection.class,
                contexts.get(0).getConnectSession());
        InternalHttpConnection secondConnection = assertInstanceOf(InternalHttpConnection.class,
                contexts.get(1).getConnectSession());
        assertTrue(firstConnection != secondConnection, "一请求一连接");
        assertEquals(1, firstConnection.sequence());
        assertEquals(2, secondConnection.sequence());
        channel.finishAndReleaseAll();
    }

    @Test
    void rawRequestPassesThroughUnorchestrated() {
        List<HttpContext> contexts = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(
                HttpRequestSchedulerChannelHandler.builder().orchestrator(orchestrator(contexts)).build());
        FullHttpRequest upgrade = getRequest("/ws");
        upgrade.headers().set("Upgrade", "websocket");
        FullHttpRequest plain = getRequest("/a");

        // 升级协议握手与未封章请求都不经本处理器，原样透传交由下游决定
        channel.writeInbound(upgrade);
        channel.writeInbound(plain);

        assertSame(upgrade, channel.readInbound(), "升级协议请求原样透传");
        assertSame(plain, channel.readInbound(), "未封章请求原样透传");
        assertTrue(contexts.isEmpty(), "未封章请求不经编排");
        channel.finishAndReleaseAll();
    }
}
