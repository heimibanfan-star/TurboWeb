package top.turboweb.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;
import top.turboweb.http.core.ByteRequestMessage;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;
import top.turboweb.http.core.StatusLine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 响应式客户端引擎的语义、边界与并发测试：传输行为以本机 JDK HttpServer 为对端验证，
 * 取消与完成的竞态以多轮赛跑钉住"终态回调恰一次"不变量。
 */
class ReactiveHttpClientEngineTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private int port;
    /** 各用例创建的引擎统一登记，收尾统一释放 */
    private final List<ReactiveHttpClientEngine> engines = new ArrayList<>();
    /** 服务端记录的最近一次请求属性：方法、体长度与指定请求头取值 */
    private final ConcurrentMap<String, String> seen = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        seen.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        registerContexts();
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopAll() {
        for (ReactiveHttpClientEngine engine : engines) {
            engine.close();
        }
        engines.clear();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    /**
     * 注册各测试端点：回显、状态码、分块流、滴流与慢响应。
     */
    private void registerContexts() {
        // 固定体与同名多值响应头
        server.createContext("/ok", exchange -> {
            byte[] body = "hello-body".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("X-Test", "a");
            exchange.getResponseHeaders().add("X-Test", "b");
            respond(exchange, 200, body);
        });
        server.createContext("/status-500", exchange ->
                respond(exchange, 500, "err".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/status-302", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        // 请求体回显，并记录方法、体长与请求头观察值
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            seen.put("method", exchange.getRequestMethod());
            seen.put("body-length", String.valueOf(body.length));
            seen.put("content-type", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
            seen.put("x-custom", String.valueOf(exchange.getRequestHeaders().getFirst("X-Custom")));
            seen.put("accept-encoding", String.valueOf(exchange.getRequestHeaders().getFirst("Accept-Encoding")));
            respond(exchange, 200, body);
        });
        // 分块流：两个分块间留间隔，验证流式回调序列
        server.createContext("/chunked", exchange -> {
            try {
                exchange.getResponseHeaders().set("X-Test", "a");
                exchange.getResponseHeaders().add("X-Test", "b");
                exchange.sendResponseHeaders(200, 0);
                OutputStream out = exchange.getResponseBody();
                out.write("chunk-one-".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(200);
                out.write("chunk-two".getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // 持续滴流：每 100ms 一块，直至对端断开，供取消用例使用
        server.createContext("/drip", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            try {
                while (true) {
                    out.write("drip".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException ignored) {
                // 取消即断连，按对端断开收场
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                seen.put("drip-closed", "1");
                exchange.close();
            }
        });
        // 慢响应：聚合等待中断与响应超时用例使用
        server.createContext("/slow-body", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "late".getBytes(StandardCharsets.UTF_8));
        });
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private ReactiveHttpClientEngine newEngine(Consumer<ReactiveClientConfig.Builder> customizer) {
        ReactiveClientConfig.Builder builder = ReactiveClientConfig.builder();
        customizer.accept(builder);
        ReactiveHttpClientEngine engine = new ReactiveHttpClientEngine(builder.build());
        engines.add(engine);
        return engine;
    }

    private ReactiveHttpClientEngine newEngine() {
        return newEngine(builder -> { });
    }

    private String uri(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static ByteRequestMessage request(HttpRequestMethod method, String target, byte[] content) {
        return new ByteRequestMessage(
                new RequestLine(method, new RequestTarget(target)), content);
    }

    private static ByteRequestMessage get(String target) {
        return request(HttpRequestMethod.GET, target, new byte[0]);
    }

    /**
     * 已关闭端口的本地地址：先占住端口再释放，连接必然被拒。
     */
    private static String refusedUri() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/ok";
        }
    }

    @Nested
    @DisplayName("入口边界")
    class EntryBoundaries {

        @Test
        @DisplayName("配置为空拒绝")
        void nullConfigRejected() {
            assertThrows(NullPointerException.class, () -> new ReactiveHttpClientEngine(null));
        }

        @Test
        @DisplayName("请求消息为空拒绝")
        void nullRequestRejected() {
            ReactiveHttpClientEngine engine = newEngine();
            assertThrows(NullPointerException.class, () -> engine.request(null));
            assertThrows(NullPointerException.class, () -> engine.stream(null, noopHandler()));
        }

        @Test
        @DisplayName("流式回调为空拒绝")
        void nullHandlerRejected() {
            ReactiveHttpClientEngine engine = newEngine();
            assertThrows(NullPointerException.class, () -> engine.stream(get(uri("/ok")), null));
        }

        @Test
        @DisplayName("非绝对请求目标拒绝")
        void relativeTargetRejected() {
            ReactiveHttpClientEngine engine = newEngine();
            ByteRequestMessage relative = get("/ok");
            IllegalArgumentException requestError = assertThrows(IllegalArgumentException.class,
                    () -> engine.request(relative));
            assertEquals("请求目标必须是绝对地址: /ok", requestError.getMessage());
            IllegalArgumentException streamError = assertThrows(IllegalArgumentException.class,
                    () -> engine.stream(relative, noopHandler()));
            assertEquals("请求目标必须是绝对地址: /ok", streamError.getMessage());
        }

        @Test
        @DisplayName("引擎关闭后两入口均拒绝")
        void closedEngineRejected() {
            ReactiveHttpClientEngine engine = newEngine();
            engine.close();
            assertThrows(IllegalStateException.class, () -> engine.request(get(uri("/ok"))));
            assertThrows(IllegalStateException.class, () -> engine.stream(get(uri("/ok")), noopHandler()));
        }

        @Test
        @DisplayName("关闭幂等，重复调用无副作用")
        void closeIsIdempotent() {
            ReactiveHttpClientEngine engine = newEngine();
            engine.close();
            engine.close();
        }

        private static StreamHandler noopHandler() {
            return new StreamHandler() {
                @Override
                public void onHeaders(StatusLine statusLine, Headers headers) { }

                @Override
                public void onData(byte[] chunk) { }

                @Override
                public void onComplete() { }

                @Override
                public void onThrowable(Throwable t) { }
            };
        }
    }

    @Nested
    @DisplayName("聚合请求语义")
    class RequestSemantics {

        @Test
        @DisplayName("200 响应全量聚合：状态行、体与同名多值头承接")
        void aggregatesSuccessResponse() {
            ReactiveHttpClientEngine engine = newEngine();

            ByteResponseMessage response = engine.request(get(uri("/ok")));

            assertEquals(200, response.line().code());
            assertEquals("OK", response.line().reason());
            assertEquals("hello-body", new String(response.content(), StandardCharsets.UTF_8));
            assertEquals("a", response.headers().get("X-Test"));
            assertEquals(List.of("a", "b"), response.headers().values("X-Test"));
            assertEquals("text/plain", response.headers().get("Content-Type"));
        }

        @Test
        @DisplayName("非 2xx 响应不降级，原样返回状态行")
        void non2xxReturnedAsIs() {
            ReactiveHttpClientEngine engine = newEngine();

            ByteResponseMessage response = engine.request(get(uri("/status-500")));

            assertEquals(500, response.line().code());
            assertEquals("err", new String(response.content(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("POST 请求体与请求头完整上线")
        void postBodyAndHeadersReachServer() {
            ReactiveHttpClientEngine engine = newEngine();
            byte[] payload = "request-payload".getBytes(StandardCharsets.UTF_8);
            ByteRequestMessage request = request(HttpRequestMethod.POST, uri("/echo"), payload);
            request.headers().set("Content-Type", "text/plain");
            request.headers().add("X-Custom", "v1");

            ByteResponseMessage response = engine.request(request);

            assertEquals(200, response.line().code());
            assertEquals("request-payload", new String(response.content(), StandardCharsets.UTF_8));
            assertEquals("POST", seen.get("method"));
            assertEquals("15", seen.get("body-length"));
            assertEquals("text/plain", seen.get("content-type"));
            assertEquals("v1", seen.get("x-custom"));
        }

        @Test
        @DisplayName("零长度请求体按无体上线")
        void emptyBodySentWithoutBody() {
            ReactiveHttpClientEngine engine = newEngine();

            ByteResponseMessage response =
                    engine.request(request(HttpRequestMethod.POST, uri("/echo"), new byte[0]));

            assertEquals(200, response.line().code());
            assertEquals("POST", seen.get("method"));
            assertEquals("0", seen.get("body-length"));
        }

        @Test
        @DisplayName("连接拒绝以传输异常抛出并携带原因")
        void connectionRefusedWrappedAsTransportFailure() throws IOException {
            ReactiveHttpClientEngine engine = newEngine();

            ClientTransportException e = assertThrows(ClientTransportException.class,
                    () -> engine.request(get(refusedUri())));

            assertEquals("聚合请求传输失败", e.getMessage());
            assertNotNull(e.getCause());
        }

        @Test
        @DisplayName("等待聚合响应时被中断：中断状态先行恢复，异常携带中断原因")
        void interruptionDuringAwaitRestoresFlag() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<Boolean> interruptFlagRestored = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    engine.request(get(uri("/slow-body")));
                } catch (ClientTransportException e) {
                    failure.set(e);
                    interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                }
            });
            caller.start();
            Thread.sleep(300);
            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(5));

            ClientTransportException e = assertInstanceOf(ClientTransportException.class, failure.get());
            assertEquals("等待聚合响应时被中断", e.getMessage());
            assertInstanceOf(InterruptedException.class, e.getCause());
            assertEquals(Boolean.TRUE, interruptFlagRestored.get());
        }

        @Test
        @DisplayName("响应超时生效：慢响应以传输异常收场")
        void responseTimeoutEnforced() {
            ReactiveHttpClientEngine engine = newEngine(builder -> builder.responseTimeout(Duration.ofMillis(300)));

            ClientTransportException e = assertThrows(ClientTransportException.class,
                    () -> engine.request(get(uri("/slow-body"))));

            assertEquals("聚合请求传输失败", e.getMessage());
            assertInstanceOf(ReadTimeoutException.class, e.getCause());
        }

        @Test
        @DisplayName("响应超时置空时不设超时，慢响应正常返回")
        void responseTimeoutDisabledByNull() {
            ReactiveHttpClientEngine engine = newEngine(builder -> builder.responseTimeout(null));

            ByteResponseMessage response = engine.request(get(uri("/slow-body")));

            assertEquals(200, response.line().code());
            assertEquals("late", new String(response.content(), StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("流式请求语义")
    class StreamSemantics {

        @Test
        @DisplayName("回调序列头先于体、体先于完成终态")
        void callbackSequence() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            RecordingHandler handler = new RecordingHandler();

            engine.stream(get(uri("/chunked")), handler);
            assertTrue(handler.terminal.await(5, TimeUnit.SECONDS));

            assertEquals(1, handler.terminalCount.get());
            assertEquals("complete", handler.terminalKind.get());
            assertEquals(200, handler.statusLine.get().code());
            assertEquals(List.of("a", "b"), handler.headers.get().values("X-Test"));
            assertEquals("chunk-one-chunk-two", String.join("", handler.chunks.stream()
                    .map(c -> new String(c, StandardCharsets.UTF_8))
                    .toList()));
            assertEquals("headers", handler.events.get(0));
            assertEquals("complete", handler.events.get(handler.events.size() - 1));
        }

        @Test
        @DisplayName("取消后分块不再递送，终态以取消异常收场，连接随即关闭")
        void cancelStopsDeliveryWithCancellationTerminal() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            RecordingHandler handler = new RecordingHandler();
            AtomicReference<StreamHandle> handleRef = new AtomicReference<>();
            handler.onDataAction = () -> {
                StreamHandle handle = handleRef.get();
                if (handle != null) {
                    handle.cancel();
                }
            };

            handleRef.set(engine.stream(get(uri("/drip")), handler));
            assertTrue(handler.terminal.await(5, TimeUnit.SECONDS));
            // 放宽窗口让迟到分块有机会暴露守卫缺失
            Thread.sleep(300);

            assertEquals(1, handler.terminalCount.get());
            assertEquals("throwable", handler.terminalKind.get());
            assertInstanceOf(CancellationException.class, handler.failure.get());
            assertEquals("流式请求已取消", handler.failure.get().getMessage());
            // 取消终止响应接收，服务端按断连收场
            for (int i = 0; i < 50 && !"1".equals(seen.get("drip-closed")); i++) {
                Thread.sleep(100);
            }
            assertEquals("1", seen.get("drip-closed"));
        }

        @Test
        @DisplayName("流式连接拒绝以失败终态收场")
        void streamConnectionRefusedFailsTerminal() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            RecordingHandler handler = new RecordingHandler();

            engine.stream(get(refusedUri()), handler);
            assertTrue(handler.terminal.await(5, TimeUnit.SECONDS));

            assertEquals(1, handler.terminalCount.get());
            assertEquals("throwable", handler.terminalKind.get());
            assertFalse(handler.failure.get() instanceof CancellationException);
        }

        @Test
        @DisplayName("竞态赛跑：取消与完成赛跑时终态回调恰一次")
        void cancelRacingCompletionDeliversExactlyOneTerminal() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                for (int round = 0; round < 60; round++) {
                    RecordingHandler handler = new RecordingHandler();
                    StreamHandle handle = engine.stream(get(uri("/ok")), handler);
                    pool.execute(handle::cancel);
                    assertTrue(handler.terminal.await(5, TimeUnit.SECONDS), "第 " + round + " 轮终态缺失");
                    assertEquals(1, handler.terminalCount.get(),
                            "第 " + round + " 轮终态回调不恰一次：" + handler.terminalKind.get());
                    String kind = handler.terminalKind.get();
                    assertTrue("complete".equals(kind) || "throwable".equals(kind), "未知终态 " + kind);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("并发语义")
    class ConcurrencySemantics {

        @Test
        @DisplayName("多线程并发聚合请求全量正确")
        void concurrentRequestsAllCorrect() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            int threads = 24;
            int perThread = 5;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CyclicBarrier gate = new CyclicBarrier(threads);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    gate.await(5, TimeUnit.SECONDS);
                    StringBuilder marks = new StringBuilder();
                    for (int j = 0; j < perThread; j++) {
                        byte[] payload = ("payload-" + id + "-" + j).getBytes(StandardCharsets.UTF_8);
                        ByteResponseMessage response = engine.request(
                                request(HttpRequestMethod.POST, uri("/echo"), payload));
                        marks.append(new String(response.content(), StandardCharsets.UTF_8)).append('|');
                    }
                    return marks.toString();
                }));
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

            for (int i = 0; i < threads; i++) {
                StringBuilder expected = new StringBuilder();
                for (int j = 0; j < perThread; j++) {
                    expected.append("payload-").append(i).append('-').append(j).append('|');
                }
                assertEquals(expected.toString(), futures.get(i).get());
            }
        }

        @Test
        @DisplayName("并发流式请求各自独立收齐终态")
        void concurrentStreamsAllTerminate() throws Exception {
            ReactiveHttpClientEngine engine = newEngine();
            int streams = 16;
            List<RecordingHandler> handlers = new ArrayList<>();
            for (int i = 0; i < streams; i++) {
                RecordingHandler handler = new RecordingHandler();
                handlers.add(handler);
                engine.stream(get(uri("/chunked")), handler);
            }
            for (RecordingHandler handler : handlers) {
                assertTrue(handler.terminal.await(10, TimeUnit.SECONDS));
                assertEquals(1, handler.terminalCount.get());
                assertEquals("complete", handler.terminalKind.get());
                assertEquals("chunk-one-chunk-two", String.join("", handler.chunks.stream()
                        .map(c -> new String(c, StandardCharsets.UTF_8))
                        .toList()));
            }
        }
    }

    @Nested
    @DisplayName("装配观察")
    class AssemblyObservation {

        @Test
        @DisplayName("解压关闭时请求不携带压缩协商头")
        void compressionOffSendsNoAcceptEncoding() {
            ReactiveHttpClientEngine engine = newEngine(builder -> builder.compression(false));

            engine.request(get(uri("/echo")));

            assertEquals("null", seen.get("accept-encoding"));
        }

        @Test
        @DisplayName("解压开启时请求携带压缩协商头")
        void compressionOnSendsAcceptEncoding() {
            ReactiveHttpClientEngine engine = newEngine(builder -> builder.compression(true));

            engine.request(get(uri("/echo")));

            assertNotNull(seen.get("accept-encoding"));
        }

        @Test
        @DisplayName("重定向默认不跟随，302 原样返回")
        void redirectNotFollowedByDefault() {
            ReactiveHttpClientEngine engine = newEngine();

            ByteResponseMessage response = engine.request(get(uri("/status-302")));

            assertEquals(302, response.line().code());
        }

        @Test
        @DisplayName("池参数全量装配可用，照常请求")
        void fullyFeaturedAssemblyServesRequest() {
            ReactiveHttpClientEngine engine = newEngine(builder -> builder
                    .maxConnections(32)
                    .pendingAcquireMaxCount(64)
                    .pendingAcquireTimeout(Duration.ofSeconds(5))
                    .maxIdleTime(Duration.ofSeconds(30))
                    .maxLifeTime(Duration.ofMinutes(5))
                    .eventLoopCount(2)
                    .followRedirect(false)
                    .wiretap(true));

            ByteResponseMessage response = engine.request(get(uri("/ok")));

            assertEquals(200, response.line().code());
        }

        @Test
        @DisplayName("透传连接池与 TLS 配置可装配并正常服务请求")
        void passthroughPoolAssemblyServesRequest() throws Exception {
            ConnectionProvider pool = ConnectionProvider.builder("passthrough-pool").build();
            SslProvider ssl = SslProvider.builder()
                    .sslContext(SslContextBuilder.forClient().build())
                    .build();
            ReactiveHttpClientEngine engine = newEngine(builder -> builder
                    .connectionProvider(pool)
                    .sslProvider(ssl));

            ByteResponseMessage response = engine.request(get(uri("/ok")));

            assertEquals(200, response.line().code());
        }

        @Test
        @DisplayName("引擎关闭不释放透传连接池，池仍可用")
        void engineCloseLeavesPassthroughPoolUsable() throws Exception {
            ConnectionProvider pool = ConnectionProvider.builder("shared-pool").build();
            ReactiveHttpClientEngine first = newEngine(builder -> builder.connectionProvider(pool));
            assertEquals(200, first.request(get(uri("/ok"))).line().code());
            first.close();

            ReactiveHttpClientEngine second = newEngine(builder -> builder.connectionProvider(pool));
            ByteResponseMessage response = second.request(get(uri("/ok")));

            assertEquals(200, response.line().code());
        }

        @Test
        @DisplayName("代理配置可装配，装配本身不发连接")
        void proxyOptionsAssemblyConstructs() {
            newEngine(builder -> builder.proxyOptions(spec -> spec
                    .type(ProxyProvider.Proxy.HTTP)
                    .host("127.0.0.1").port(1)))
                    .close();
        }
    }

    /**
     * 记录型流式回调：按序登记事件、收集分块，终态计数用于钉"恰一次"不变量；
     * onDataAction 供用例在分块到达时注入动作（如取消）。
     */
    private static class RecordingHandler implements StreamHandler {

        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<byte[]> chunks = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<StatusLine> statusLine = new AtomicReference<>();
        final AtomicReference<Headers> headers = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<String> terminalKind = new AtomicReference<>();
        final AtomicInteger terminalCount = new AtomicInteger();
        final CountDownLatch terminal = new CountDownLatch(1);
        volatile Runnable onDataAction = () -> { };

        @Override
        public void onHeaders(StatusLine line, Headers messageHeaders) {
            events.add("headers");
            statusLine.set(line);
            headers.set(new Headers(messageHeaders));
        }

        @Override
        public void onData(byte[] chunk) {
            events.add("data");
            chunks.add(chunk);
            onDataAction.run();
        }

        @Override
        public void onComplete() {
            events.add("complete");
            terminalKind.set("complete");
            terminalCount.incrementAndGet();
            terminal.countDown();
        }

        @Override
        public void onThrowable(Throwable t) {
            events.add("throwable");
            failure.set(t);
            terminalKind.set("throwable");
            terminalCount.incrementAndGet();
            terminal.countDown();
        }
    }
}
