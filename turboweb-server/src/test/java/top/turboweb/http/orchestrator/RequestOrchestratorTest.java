package top.turboweb.http.orchestrator;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.LimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.UnlimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.VirtualThreadResource;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.respmeta.DefaultResponseMeta;
import top.turboweb.http.context.respmeta.ResponseMeta;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.core.session.BackHoleSessionManager;
import top.turboweb.http.core.session.SessionManager;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.serializer.JacksonJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link RequestOrchestrator} 的行为锚：建造者装配八组件（中间件、虚拟线程资源、
 * 响应写出器管理器、结果转化器管理器、cors 配置、异常处理器管理器、
 * 会话管理器持有者、json 序列化器）的构造契约——配置方法 null 入参即时拒收、
 * 缺配在 build 时拒收且消息指名组件、八件齐配构建成功且重复 build 产物互不相同；
 * CORS 来源处理流程的响应头写出契约；以及消费流程契约——
 * 入口参数判空、中间件结果转化写出与请求释放、挂载的响应元信息不回填响应、
 * OPTIONS 预检空 200 不进链、未注册异常落 500 兜底页、路由未匹配落 404 页、
 * 注册处理器返回值透传写出、异常响应同样带 CORS 头、提交被拒写出 429 并释放请求；
 * 另锚请求日志文案的格式化契约——耗时按阈值分毫秒与微秒两档、写出状态措辞。
 */
class RequestOrchestratorTest {

    /** 按指定 cors 配置装配九组件齐备的编排器（空链头、空异常处理器表）。 */
    private static RequestOrchestrator orchestratorWith(HttpCorsConfig corsConfig) {
        return RequestOrchestrator.builder()
                .middleware(new SentinelMiddleware())
                .virtualThreadResource(new UnlimitedVirtualThreadResource("test-orchestrator"))
                .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                .corsConfig(corsConfig)
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())
                .jsonSerializer(new JacksonJsonSerializer())
                .defaultCharset(StandardCharsets.UTF_8)
                .build();
    }

    /** 按指定链头与异常处理器装配九组件齐备的编排器（默认通配 cors、非受限虚拟线程资源）。 */
    private static RequestOrchestrator flowOrchestrator(Middleware chain, ExceptionHandlerManager handlers) {
        return flowOrchestrator(chain, handlers, new UnlimitedVirtualThreadResource("test-orchestrator"));
    }

    /** 按指定链头、异常处理器与虚拟线程资源装配九组件齐备的编排器。 */
    private static RequestOrchestrator flowOrchestrator(Middleware chain, ExceptionHandlerManager handlers,
                                                         VirtualThreadResource resource) {
        return RequestOrchestrator.builder()
                .middleware(chain)
                .virtualThreadResource(resource)
                .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(handlers)
                .sessionManagerHolder(() -> new BackHoleSessionManager())
                .jsonSerializer(new JacksonJsonSerializer())
                .defaultCharset(StandardCharsets.UTF_8)
                .build();
    }

    /** 创建指定方法与 Origin 的根路径请求；origin 为 null 表示不带 Origin 头。 */
    private static FullHttpRequest requestWith(HttpMethod method, String origin) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, "/");
        if (origin != null) {
            request.headers().set("Origin", origin);
        }
        return request;
    }

    /** 创建带 Origin 头的 GET 请求；origin 为 null 表示不带该头。 */
    private static FullHttpRequest requestWithOrigin(String origin) {
        return requestWith(HttpMethod.GET, origin);
    }

    private static HttpResponse newResponse() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    @Test
    void nullComponentRejectedAtSetter() {
        RequestOrchestrator.Builder builder = RequestOrchestrator.builder();

        NullPointerException e1 = assertThrows(NullPointerException.class, () -> builder.middleware(null));
        assertEquals("middleware must not be null", e1.getMessage());
        NullPointerException e2 = assertThrows(NullPointerException.class, () -> builder.virtualThreadResource(null));
        assertEquals("virtual thread resource must not be null", e2.getMessage());
        NullPointerException e3 = assertThrows(NullPointerException.class, () -> builder.responseWriterManager(null));
        assertEquals("response writer manager must not be null", e3.getMessage());
        NullPointerException e4 = assertThrows(NullPointerException.class, () -> builder.resultConverterManager(null));
        assertEquals("result converter manager must not be null", e4.getMessage());
        NullPointerException e5 = assertThrows(NullPointerException.class, () -> builder.corsConfig(null));
        assertEquals("cors config must not be null", e5.getMessage());
        NullPointerException e6 = assertThrows(NullPointerException.class, () -> builder.exceptionHandlerManager(null));
        assertEquals("exception handler manager must not be null", e6.getMessage());
        NullPointerException e7 = assertThrows(NullPointerException.class, () -> builder.sessionManagerHolder(null));
        assertEquals("session manager holder must not be null", e7.getMessage());
        NullPointerException e8 = assertThrows(NullPointerException.class, () -> builder.jsonSerializer(null));
        assertEquals("json serializer must not be null", e8.getMessage());
        NullPointerException e9 = assertThrows(NullPointerException.class, () -> builder.defaultCharset(null));
        assertEquals("default charset must not be null", e9.getMessage());
    }

    @Test
    void missingComponentRejectedAtBuild() {
        // 每次缺配一件，build 必须指名缺失组件拒收（校验按装配次序短路，先缺先报）
        NullPointerException e1 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("middleware must not be null", e1.getMessage());

        NullPointerException e2 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("virtual thread resource must not be null", e2.getMessage());

        NullPointerException e3 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("response writer manager must not be null", e3.getMessage());

        NullPointerException e4 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("result converter manager must not be null", e4.getMessage());

        NullPointerException e5 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .build());
        assertEquals("cors config must not be null", e5.getMessage());

        NullPointerException e6 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .build());
        assertEquals("exception handler manager must not be null", e6.getMessage());

        NullPointerException e7 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .jsonSerializer(new JacksonJsonSerializer())
                        .build());
        assertEquals("session manager holder must not be null", e7.getMessage());

        NullPointerException e8 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .sessionManagerHolder(() -> new BackHoleSessionManager())
                        .build());
        assertEquals("json serializer must not be null", e8.getMessage());

        NullPointerException e9 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                        .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .sessionManagerHolder(() -> new BackHoleSessionManager())
                        .jsonSerializer(new JacksonJsonSerializer())
                        .build());
        assertEquals("default charset must not be null", e9.getMessage());
    }

    @Test
    void fullComponentsBuildAndRepeatedBuildIndependent() {
        RequestOrchestrator.Builder builder = RequestOrchestrator.builder()
                .middleware(new SentinelMiddleware())
                .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())
                .jsonSerializer(new JacksonJsonSerializer())
                .defaultCharset(StandardCharsets.UTF_8);

        RequestOrchestrator first = builder.build();
        RequestOrchestrator second = builder.build();

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second, "重复 build 产出互不相同的实例");
    }

    @Test
    void wildcardDefaultWritesAllCorsHeaders() {
        RequestOrchestrator orchestrator = orchestratorWith(HttpCorsConfig.builder().build());
        HttpResponse response = newResponse();

        orchestrator.handleCorsHeaders(requestWithOrigin("https://a.com"), response);

        assertEquals("https://a.com", response.headers().get("Access-Control-Allow-Origin"), "通配配置回声实际来源");
        assertEquals("GET,POST,PUT,DELETE", response.headers().get("Access-Control-Allow-Methods"), "方法列表逗号 join");
        assertEquals("*", response.headers().get("Access-Control-Allow-Headers"), "请求头通配写字面星号");
        assertEquals("Content-Disposition", response.headers().get("Access-Control-Expose-Headers"));
        assertEquals("3600", response.headers().get("Access-Control-Max-Age"));
        assertFalse(response.headers().contains("Access-Control-Allow-Credentials"), "默认不允许携带凭证");
    }

    @Test
    void absentOrEmptyOriginFallsBackToNullLiteral() {
        RequestOrchestrator orchestrator = orchestratorWith(HttpCorsConfig.builder().build());

        HttpResponse absent = newResponse();
        orchestrator.handleCorsHeaders(requestWithOrigin(null), absent);
        assertEquals("null", absent.headers().get("Access-Control-Allow-Origin"), "无 Origin 头回落字面 null");

        HttpResponse empty = newResponse();
        orchestrator.handleCorsHeaders(requestWithOrigin(""), empty);
        assertEquals("null", empty.headers().get("Access-Control-Allow-Origin"), "空 Origin 头同样回落字面 null");
    }

    @Test
    void exactOriginWhitelistAcceptsAndRejects() {
        HttpCorsConfig config = HttpCorsConfig.builder()
                .allowedOrigins(List.of("https://a.com"))
                .build();
        RequestOrchestrator orchestrator = orchestratorWith(config);

        HttpResponse allowed = newResponse();
        orchestrator.handleCorsHeaders(requestWithOrigin("https://a.com"), allowed);
        assertEquals("https://a.com", allowed.headers().get("Access-Control-Allow-Origin"), "白名单内来源回声放行");

        HttpResponse rejected = newResponse();
        orchestrator.handleCorsHeaders(requestWithOrigin("https://evil.com"), rejected);
        assertNull(rejected.headers().get("Access-Control-Allow-Origin"), "白名单外来源不写放行头");
        assertFalse(rejected.headers().contains("Access-Control-Max-Age"), "白名单外来源任何 CORS 头都不写");
    }

    @Test
    void credentialsCombinationBranches() {
        HttpResponse echoed = newResponse();
        orchestratorWith(HttpCorsConfig.builder()
                .allowedOrigins(List.of("https://a.com"))
                .allowCredentials(true)
                .build())
                .handleCorsHeaders(requestWithOrigin("https://a.com"), echoed);
        assertEquals("true", echoed.headers().get("Access-Control-Allow-Credentials"));
        assertEquals("https://a.com", echoed.headers().get("Access-Control-Allow-Origin"), "凭证模式下回声精确来源");

        // 凭证开启且来源为字面星号：CORS 标准禁止组合，整体跳过不写任何头
        HttpResponse star = newResponse();
        orchestratorWith(HttpCorsConfig.builder().allowCredentials(true).build())
                .handleCorsHeaders(requestWithOrigin("*"), star);
        assertFalse(star.headers().contains("Access-Control-Allow-Credentials"), "凭证与字面星号组合整体跳过");
        assertNull(star.headers().get("Access-Control-Allow-Origin"));

        HttpResponse denied = newResponse();
        orchestratorWith(HttpCorsConfig.builder()
                .allowedOrigins(List.of("https://a.com"))
                .allowCredentials(true)
                .build())
                .handleCorsHeaders(requestWithOrigin("https://evil.com"), denied);
        assertEquals("true", denied.headers().get("Access-Control-Allow-Credentials"), "凭证头先于放行门写出");
        assertNull(denied.headers().get("Access-Control-Allow-Origin"), "来源未过放行门不写放行头");
    }

    @Test
    void wildcardListsRenderLiteralStarAndMaxAgeConverted() {
        HttpCorsConfig config = HttpCorsConfig.builder()
                .allowedMethods(List.of("*"))
                .allowedHeaders(List.of("X-Token", "X-Other"))
                .exposedHeaders(List.of("*"))
                .maxAge(600)
                .build();
        HttpResponse response = newResponse();

        orchestratorWith(config).handleCorsHeaders(requestWithOrigin("https://a.com"), response);

        assertEquals("*", response.headers().get("Access-Control-Allow-Methods"), "方法通配写字面星号");
        assertEquals("X-Token,X-Other", response.headers().get("Access-Control-Allow-Headers"), "精确请求头逗号 join");
        assertEquals("*", response.headers().get("Access-Control-Expose-Headers"), "暴露头通配写字面星号");
        assertEquals("600", response.headers().get("Access-Control-Max-Age"), "maxAge 数值转串");
    }

    @Test
    void nullArgumentsRejectedAtFlowEntry() {
        RequestOrchestrator orchestrator = orchestratorWith(HttpCorsConfig.builder().build());
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        NullPointerException e1 = assertThrows(NullPointerException.class, () -> orchestrator.orchestrate(null, session));
        assertEquals("request must not be null", e1.getMessage());
        NullPointerException e2 = assertThrows(NullPointerException.class, () -> orchestrator.orchestrate(request, null));
        assertEquals("connect session must not be null", e2.getMessage());
        NullPointerException e3 = assertThrows(NullPointerException.class, () -> orchestrator.doOrchestrate(null, session));
        assertEquals("request must not be null", e3.getMessage());
        NullPointerException e4 = assertThrows(NullPointerException.class, () -> orchestrator.doOrchestrate(request, null));
        assertEquals("connect session must not be null", e4.getMessage());

        assertNull(channel.readOutbound(), "入口判空拒收不产生任何出站");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void chainResultWrittenWithCorsAndRequestReleased() {
        FixedResultMiddleware chain = new FixedResultMiddleware("hello");
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertTrue(chain.invoked().get(), "普通请求进入中间件链");
        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus());
        assertEquals("hello", written.content().toString(StandardCharsets.UTF_8), "链结果经转化链按字符串产出响应");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "正常响应写出 CORS 头");
        assertEquals(0, request.refCnt(), "写出提交后请求已释放");
        channel.finishAndReleaseAll();
    }

    @Test
    void defaultCharsetFlowsToResponseEncoding() {
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        FixedResultMiddleware chain = new FixedResultMiddleware("你好");
        RequestOrchestrator orchestrator = RequestOrchestrator.builder()
                .middleware(chain)
                .virtualThreadResource(new UnlimitedVirtualThreadResource("t"))
                .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())
                .jsonSerializer(new JacksonJsonSerializer())
                .defaultCharset(gbk)
                .build();
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals("text/html;charset=GBK", written.headers().get("Content-Type"),
                "装配期缺省字符集贯通转化链决定响应编码");
        assertEquals("你好", written.content().toString(gbk), "正文与声明同源编码");
        assertEquals(0, request.refCnt(), "写出提交后请求已释放");
        channel.finishAndReleaseAll();
    }

    /** 按指定链头与会话管理器实例装配九组件齐备的编排器（默认通配 cors、非受限虚拟线程资源）。 */
    private static RequestOrchestrator orchestratorWithChainAndManager(Middleware chain, SessionManager manager) {
        return RequestOrchestrator.builder()
                .middleware(chain)
                .virtualThreadResource(new UnlimitedVirtualThreadResource("test-token-flow"))
                .responseWriterManager(new ResponseWriterManager(Stubs.threadPool(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> manager)
                .jsonSerializer(new JacksonJsonSerializer())
                .defaultCharset(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void safeScopeWrapsChainExecutionAndExitsAfter() {
        List<String> events = new ArrayList<>();
        RequestOrchestrator orchestrator = orchestratorWithChainAndManager(
                new EventRecordingMiddleware(events), new ScopeRecordingSessionManager(events));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertEquals(List.of("enter", "chain", "exit"), events,
                "编排器把链执行整体放入会话管理器的安全作用域，链结果产出后作用域退出");
        channel.finishAndReleaseAll();
    }

    @Test
    void safeScopeExitsWhenChainThrows() {
        List<String> events = new ArrayList<>();
        ThrowingMiddleware chain = new ThrowingMiddleware(new IllegalStateException("boom"));
        RequestOrchestrator orchestrator = orchestratorWithChainAndManager(
                chain, new ScopeRecordingSessionManager(events));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertEquals(List.of("enter", "exit"), events,
                "链抛出异常时作用域同样先退出再进入异常处理路径，兜底页不泄漏读锁");
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus());
        channel.finishAndReleaseAll();
    }

    @Test
    void optionsPreflightDoesNotEnterSafeScope() {
        List<String> events = new ArrayList<>();
        FixedResultMiddleware chain = new FixedResultMiddleware("must-not-be-written");
        RequestOrchestrator orchestrator = orchestratorWithChainAndManager(
                chain, new ScopeRecordingSessionManager(events));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.OPTIONS, "https://a.com");

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertTrue(events.isEmpty(), "OPTIONS 预检不进 startMiddleware，不进入安全作用域");
        channel.finishAndReleaseAll();
    }

    @Test
    void attachedResponseMetaIsNotAppliedToResponse() {
        RequestOrchestrator orchestrator = flowOrchestrator(new MetaAttachingMiddleware(), new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "挂载的响应元信息不再回填状态码");
        String contentType = written.headers().get("Content-Type");
        assertTrue(contentType.startsWith("text/html"), "挂载的响应元信息不再回填内容类型，按转化链字符串支产出");
        assertFalse(contentType.contains("application/json"));
        assertEquals("hello", written.content().toString(StandardCharsets.UTF_8));
        assertEquals(0, request.refCnt(), "写出提交后请求已释放");
        channel.finishAndReleaseAll();
    }

    @Test
    void optionsPreflightAnswersEmptyOkWithoutEnteringChain() {
        FixedResultMiddleware chain = new FixedResultMiddleware("must-not-be-written");
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.OPTIONS, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertFalse(chain.invoked().get(), "OPTIONS 预检不进中间件链");
        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "预检直接回 200");
        assertEquals("0", written.headers().get("Content-Length"), "预检空正文");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "预检写出 CORS 头");
        assertEquals("3600", written.headers().get("Access-Control-Max-Age"));
        assertEquals(0, request.refCnt(), "预检路径同样释放请求");
        channel.finishAndReleaseAll();
    }

    @Test
    void unregisteredThrowableFallsToServerErrorPageStillWithCors() {
        ThrowingMiddleware chain = new ThrowingMiddleware(new IllegalStateException("boom"));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus(), "无注册处理器的异常落 500 兜底");
        String body = written.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("500 Internal Server Error"), "兜底页为 500 错误页");
        assertTrue(body.contains("java.lang.IllegalStateException"), "页面携带异常类型");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "异常响应同样写出 CORS 头");
        assertEquals(0, request.refCnt(), "异常路径同样释放请求");
        channel.finishAndReleaseAll();
    }

    @Test
    void routerNotMatchThrowableFallsToNotFoundPage() {
        ThrowingMiddleware chain = new ThrowingMiddleware(
                new RouteHandlerException("router not found: GET /missing", RouteHandlerException.ROUTER_NOT_MATCH));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.NOT_FOUND, written.getStatus(), "路由未匹配异常落 404 页");
        String body = written.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("404 Not Found"), "页面为 404 错误页");
        assertTrue(body.contains("router not found: GET /missing"), "页面携带未匹配消息");
        assertEquals(0, request.refCnt());
        channel.finishAndReleaseAll();
    }

    @Test
    void registeredHandlerValueWrittenAsResponse() {
        ThrowingMiddleware chain = new ThrowingMiddleware(new IllegalStateException("boom"));
        ExceptionHandlerManager handlers = new ExceptionHandlerManager(Set.of(new StateFailureHandler()));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, handlers);
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalConnectSession(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "处理器返回值经结果转化链正常产出");
        assertEquals("handled:boom", written.content().toString(StandardCharsets.UTF_8), "透传处理器返回值");
        assertEquals(0, request.refCnt());
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectedOrchestrationWritesTooManyRequestsAndReleases() throws InterruptedException {
        LimitedVirtualThreadResource resource = new LimitedVirtualThreadResource(1, 0, 0, "reject-flow", false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        resource.run(() -> {
            started.countDown();
            try {
                hold.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, rejectionThread -> fail("占额任务不应被拒绝"));
        assertTrue(started.await(2, TimeUnit.SECONDS), "占额任务已启动并持有唯一资源");
        try {
            FixedResultMiddleware chain = new FixedResultMiddleware("not-used");
            RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()), resource);
            EmbeddedChannel channel = new EmbeddedChannel();
            FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

            orchestrator.orchestrate(request, new InternalConnectSession(channel));

            assertFalse(chain.invoked().get(), "拒绝分支不进中间件链");
            FullHttpResponse written = channel.readOutbound();
            assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, written.getStatus(), "拒绝分支写出 429");
            assertEquals("too many request", written.content().toString(StandardCharsets.UTF_8), "429 正文与旧线一致");
            assertFalse(written.headers().contains("Access-Control-Allow-Origin"), "拒绝分支不经 CORS 处理");
            assertEquals(0, request.refCnt(), "拒绝分支同样释放请求");
            channel.finishAndReleaseAll();
        } finally {
            hold.countDown();
        }
    }

    // 第十三批锚：请求日志文案格式化——阈值 1000000 纳秒以上按毫秒计、以下（含恰好 1 毫秒）按微秒计，
    // 状态措辞 success/fail，与旧线调度器输出逐字一致。
    @Test
    void requestLogMessageUsesMillisAboveOneMillisecond() {
        assertEquals("GET /api/item cost:2ms state:success",
                RequestOrchestrator.buildRequestLogMessage("GET", "/api/item", 2_500_000L, true));
        assertEquals("GET /api/item cost:1ms state:success",
                RequestOrchestrator.buildRequestLogMessage("GET", "/api/item", 1_000_001L, true),
                "恰超阈值按毫秒计（整除取商）");
    }

    @Test
    void requestLogMessageUsesMicrosecondsUpToOneMillisecond() {
        assertEquals("GET /api/item cost:1000us state:success",
                RequestOrchestrator.buildRequestLogMessage("GET", "/api/item", 1_000_000L, true),
                "恰好 1 毫秒走微秒档（旧线阈值为严格大于）");
        assertEquals("GET /api/item cost:500us state:success",
                RequestOrchestrator.buildRequestLogMessage("GET", "/api/item", 500_000L, true));
    }

    @Test
    void requestLogMessageMarksFailStateOnUnsuccessfulWrite() {
        assertEquals("POST /submit cost:12us state:fail",
                RequestOrchestrator.buildRequestLogMessage("POST", "/submit", 12_345L, false),
                "微秒整除取商、失败措辞 fail");
    }

    /** 链成员：进链时记录 chain 事件并返回固定字符串。 */
    private static class EventRecordingMiddleware extends Middleware {
        private final List<String> events;

        EventRecordingMiddleware(List<String> events) {
            this.events = events;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            events.add("chain");
            return "hello";
        }
    }

    /** 记录安全作用域进出次序的会话管理器桩；其余方法不被编排流程触发，留空实现。 */
    private static class ScopeRecordingSessionManager implements SessionManager {
        private final List<String> events;

        ScopeRecordingSessionManager(List<String> events) {
            this.events = events;
        }

        @Override
        public <T> T sessionSafeScope(Function<SessionManager, T> action) {
            events.add("enter");
            try {
                return action.apply(this);
            } finally {
                events.add("exit");
            }
        }

        @Override
        public void setAttr(String sessionId, String key, Object value) {
        }

        @Override
        public void setAttr(String sessionId, String key, Object value, long timeout) {
        }

        @Override
        public Object getAttr(String sessionId, String key) {
            return null;
        }

        @Override
        public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
            return null;
        }

        @Override
        public void remAttr(String sessionId, String key) {
        }

        @Override
        public boolean exist(String sessionId) {
            return false;
        }

        @Override
        public boolean createSessionMap(String sessionId) {
            return false;
        }

        @Override
        public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
        }

        @Override
        public String sessionManagerName() {
            return "token recording session manager";
        }

        @Override
        public void expireAt(String sessionId) {
        }
    }

    /** 返回固定结果并记录是否被进入的链成员。 */
    private static class FixedResultMiddleware extends Middleware {
        private final Object result;
        private final AtomicBoolean invoked = new AtomicBoolean();

        FixedResultMiddleware(Object result) {
            this.result = result;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            invoked.set(true);
            return result;
        }

        AtomicBoolean invoked() {
            return invoked;
        }
    }

    /** 抛出固定异常的链成员。 */
    private static class ThrowingMiddleware extends Middleware {
        private final RuntimeException failure;

        ThrowingMiddleware(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            throw failure;
        }
    }

    /** 链成员：挂载 204 与 application/json 的响应元信息后返回固定字符串。 */
    private static class MetaAttachingMiddleware extends Middleware {
        @Override
        public Object invoke(HttpContext ctx) {
            DefaultResponseMeta meta = new DefaultResponseMeta();
            meta.status(HttpResponseStatus.NO_CONTENT);
            meta.contentType(ContentType.create("application/json", StandardCharsets.UTF_8));
            ctx.attachment().attach(ResponseMeta.ATTACH_KEY, meta);
            return "hello";
        }
    }

    /** 异常处理器：认领 IllegalStateException 并返回固定字符串。 */
    public static class StateFailureHandler {
        public String handleStateFailure(IllegalStateException failure) {
            return "handled:" + failure.getMessage();
        }
    }
}
