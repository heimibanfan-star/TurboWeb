package top.turboweb.http.orchestrator;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.MicroTaskExecutor.FinallyType;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;
import top.turboweb.http.context.*;
import top.turboweb.http.context.respmeta.DefaultResponseMeta;
import top.turboweb.http.context.respmeta.ResponseMeta;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.session.BackHoleSessionManager;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.testkit.Requests;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.Content;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.server.runtime.ResourceManager;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RequestOrchestrator} 的行为锚：建造者装配九组件（中间件、资源管理器、
 * 响应写出器管理器、结果转化器管理器、cors 配置、异常处理器管理器、
 * 会话管理器持有者、缺省字符集、请求体内容解析器管理器）的构造契约——
 * 配置方法 null 入参即时拒收、缺配在 build 时拒收且消息指名组件、
 * 九件齐配构建成功且重复 build 产物互不相同；
 * CORS 来源处理流程的响应头写出契约；以及消费流程契约——
 * 入口参数判空、中间件结果转化写出与请求释放、挂载的响应元信息不回填响应、
 * OPTIONS 预检空 200 不进链、未注册异常落 500 兜底页、路由未匹配落 404 页、
 * 注册处理器返回值透传写出、异常响应同样带 CORS 头、提交被拒写出 429 并释放请求、
 * 会话缺失与线程创建失败同样在异常抛出前释放请求；
 * 另锚请求日志文案的格式化契约——耗时按阈值分毫秒与微秒两档、写出状态措辞。
 */
class RequestOrchestratorTest {

    /** 内置解析器构成的内容解析器管理器（UTF-8 缺省字符集、无自定义解析器）。 */
    private static HttpContentParserManager contentParserManager() {
        return new HttpContentParserManager(StandardCharsets.UTF_8, List.of());
    }

    /** 测试资源管理器：以内置空间登记给定微任务执行器与桩磁盘IO执行器，供编排器与写出器管理器按名获取。 */
    private static ResourceManager testResourceManager(MicroTaskExecutor executor) {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.MICRO_TASK_EXECUTOR, executor);
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, Stubs.noopExecutor());
        return new ResourceManager.Builder().build(builtins);
    }

    /** 测试资源管理器：以内置空间只登记桩磁盘IO执行器，供响应写出器管理器按名获取。 */
    private static ResourceManager writerResourceManager() {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, Stubs.noopExecutor());
        return new ResourceManager.Builder().build(builtins);
    }

    /** 按指定 cors 配置装配九组件齐备的编排器（空链头、空异常处理器表）。 */
    private static RequestOrchestrator orchestratorWith(HttpCorsConfig corsConfig) {
        return RequestOrchestrator.builder()
                .middleware(new SentinelMiddleware())
                .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("test-orchestrator")))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(corsConfig)
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(contentParserManager())
                .build();
    }

    /** 按指定链头与异常处理器装配九组件齐备的编排器（默认通配 cors、非受限虚拟线程资源）。 */
    private static RequestOrchestrator flowOrchestrator(Middleware chain, ExceptionHandlerManager handlers) {
        return flowOrchestrator(chain, handlers, VirtualThreadMicroTaskExecutor.ofUnlimited("test-orchestrator"));
    }

    /** 按指定链头、异常处理器与虚拟线程资源装配九组件齐备的编排器。 */
    private static RequestOrchestrator flowOrchestrator(Middleware chain, ExceptionHandlerManager handlers,
                                                         VirtualThreadMicroTaskExecutor resource) {
        return RequestOrchestrator.builder()
                .middleware(chain)
                .resourceManager(testResourceManager(resource))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(handlers)
                .sessionManagerHolder(() -> new BackHoleSessionManager())                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(contentParserManager())
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
        NullPointerException e2 = assertThrows(NullPointerException.class, () -> builder.resourceManager(null));
        assertEquals("resource manager must not be null", e2.getMessage());
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
        NullPointerException e8 = assertThrows(NullPointerException.class, () -> builder.defaultCharset(null));
        assertEquals("default charset must not be null", e8.getMessage());
        NullPointerException e9 = assertThrows(NullPointerException.class, () -> builder.contentParserManager(null));
        assertEquals("content parser manager must not be null", e9.getMessage());
    }

    @Test
    void missingComponentRejectedAtBuild() {
        // 每次缺配一件，build 必须指名缺失组件拒收（校验按装配次序短路，先缺先报）
        NullPointerException e1 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("middleware must not be null", e1.getMessage());

        NullPointerException e2 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("resource manager must not be null", e2.getMessage());

        NullPointerException e3 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("response writer manager must not be null", e3.getMessage());

        NullPointerException e4 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .build());
        assertEquals("result converter manager must not be null", e4.getMessage());

        NullPointerException e5 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .build());
        assertEquals("cors config must not be null", e5.getMessage());

        NullPointerException e6 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .build());
        assertEquals("exception handler manager must not be null", e6.getMessage());

        NullPointerException e7 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))                        .build());
        assertEquals("session manager holder must not be null", e7.getMessage());

        NullPointerException e8 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .sessionManagerHolder(() -> new BackHoleSessionManager())                        .build());
        assertEquals("default charset must not be null", e8.getMessage());

        NullPointerException e9 = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.builder()
                        .middleware(new SentinelMiddleware())
                        .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                        .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                        .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                        .corsConfig(HttpCorsConfig.builder().build())
                        .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                        .sessionManagerHolder(() -> new BackHoleSessionManager())                        .defaultCharset(StandardCharsets.UTF_8)
                        .build());
        assertEquals("content parser manager must not be null", e9.getMessage(),
                "内容解析器管理器同为必配组件，缺配同样指名拒收");
    }

    @Test
    void fullComponentsBuildAndRepeatedBuildIndependent() {
        RequestOrchestrator.Builder builder = RequestOrchestrator.builder()
                .middleware(new SentinelMiddleware())
                .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(contentParserManager());

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
        InternalHttpConnection session = new InternalHttpConnection(channel);

        NullPointerException e1 = assertThrows(NullPointerException.class, () -> orchestrator.orchestrate(null, session));
        assertEquals("request must not be null", e1.getMessage());
        FullHttpRequest submissionRequest = requestWith(HttpMethod.GET, null);
        NullPointerException e2 = assertThrows(NullPointerException.class, () -> orchestrator.orchestrate(submissionRequest, null));
        assertEquals("connect session must not be null", e2.getMessage());
        assertEquals(1, submissionRequest.refCnt(), "会话缺失时只归还编排器自持的引用，调用方引用不受影响");
        submissionRequest.release();

        assertNull(channel.readOutbound(), "入口判空拒收不产生任何出站");
        channel.finishAndReleaseAll();
    }

    @Test
    void chainResultWrittenWithCorsAndCallerReferenceKept() {
        FixedResultMiddleware chain = new FixedResultMiddleware("hello");
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertTrue(chain.invoked().get(), "普通请求进入中间件链");
        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus());
        assertEquals("hello", written.content().toString(StandardCharsets.UTF_8), "链结果经转化链按字符串产出响应");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "正常响应写出 CORS 头");
        assertEquals(1, request.refCnt(), "写出后编排器归还自持引用，调用方引用仍在");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void defaultCharsetFlowsToResponseEncoding() {
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        FixedResultMiddleware chain = new FixedResultMiddleware("你好");
        RequestOrchestrator orchestrator = RequestOrchestrator.builder()
                .middleware(chain)
                .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("t")))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())                .defaultCharset(gbk)
                .contentParserManager(new HttpContentParserManager(gbk, List.of()))
                .build();
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals("text/html;charset=GBK", written.headers().get("Content-Type"),
                "装配期缺省字符集贯通转化链决定响应编码");
        assertEquals("你好", written.content().toString(gbk), "正文与声明同源编码");
        assertEquals(1, request.refCnt(), "写出后编排器归还自持引用，调用方引用仍在");
        request.release();
        channel.finishAndReleaseAll();
    }

    /** 按指定链头与会话管理器实例装配九组件齐备的编排器（默认通配 cors、非受限虚拟线程资源）。 */
    private static RequestOrchestrator orchestratorWithChainAndManager(Middleware chain, SessionManager manager) {
        return RequestOrchestrator.builder()
                .middleware(chain)
                .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("test-token-flow")))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> manager)                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(contentParserManager())
                .build();
    }

    @Test
    void safeScopeWrapsChainExecutionAndExitsAfter() {
        List<String> events = new ArrayList<>();
        RequestOrchestrator orchestrator = orchestratorWithChainAndManager(
                new EventRecordingMiddleware(events), new ScopeRecordingSessionManager(events));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(List.of("enter", "chain", "exit"), events,
                "编排器把链执行整体放入会话管理器的安全作用域，链结果产出后作用域退出");
        request.release();
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

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(List.of("enter", "exit"), events,
                "链抛出异常时作用域同样先退出再进入异常处理路径，兜底页不泄漏读锁");
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus());
        request.release();
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

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertTrue(events.isEmpty(), "OPTIONS 预检不进 startMiddleware，不进入安全作用域");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void attachedResponseMetaIsNotAppliedToResponse() {
        RequestOrchestrator orchestrator = flowOrchestrator(new MetaAttachingMiddleware(), new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "挂载的响应元信息不再回填状态码");
        String contentType = written.headers().get("Content-Type");
        assertTrue(contentType.startsWith("text/html"), "挂载的响应元信息不再回填内容类型，按转化链字符串支产出");
        assertFalse(contentType.contains("application/json"));
        assertEquals("hello", written.content().toString(StandardCharsets.UTF_8));
        assertEquals(1, request.refCnt(), "写出后编排器归还自持引用，调用方引用仍在");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void optionsPreflightAnswersEmptyOkWithoutEnteringChain() {
        FixedResultMiddleware chain = new FixedResultMiddleware("must-not-be-written");
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.OPTIONS, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertFalse(chain.invoked().get(), "OPTIONS 预检不进中间件链");
        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "预检直接回 200");
        assertEquals("0", written.headers().get("Content-Length"), "预检空正文");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "预检写出 CORS 头");
        assertEquals("3600", written.headers().get("Access-Control-Max-Age"));
        assertEquals(1, request.refCnt(), "预检路径同样只归还编排器自持的引用");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void unregisteredThrowableFallsToServerErrorPageStillWithCors() {
        ThrowingMiddleware chain = new ThrowingMiddleware(new IllegalStateException("boom"));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus(), "无注册处理器的异常落 500 兜底");
        String body = written.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("500 Internal Server Error"), "兜底页为 500 错误页");
        assertTrue(body.contains("java.lang.IllegalStateException"), "页面携带异常类型");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"), "异常响应同样写出 CORS 头");
        assertEquals(1, request.refCnt(), "异常路径同样只归还编排器自持的引用");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void routerNotMatchThrowableFallsToNotFoundPage() {
        ThrowingMiddleware chain = new ThrowingMiddleware(
                new RouteHandlerException("router not found: GET /missing", ErrorCode.RouterNotMatch));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()));
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.NOT_FOUND, written.getStatus(), "路由未匹配异常落 404 页");
        String body = written.content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("404 Not Found"), "页面为 404 错误页");
        assertTrue(body.contains("router not found: GET /missing"), "页面携带未匹配消息");
        assertEquals(1, request.refCnt());
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void registeredHandlerValueWrittenAsResponse() {
        ThrowingMiddleware chain = new ThrowingMiddleware(new IllegalStateException("boom"));
        ExceptionHandlerManager handlers = new ExceptionHandlerManager(Set.of(new StateFailureHandler()));
        RequestOrchestrator orchestrator = flowOrchestrator(chain, handlers);
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, written.getStatus(), "处理器返回值经结果转化链正常产出");
        assertEquals("handled:boom", written.content().toString(StandardCharsets.UTF_8), "透传处理器返回值");
        assertEquals(1, request.refCnt());
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectedOrchestrationWritesTooManyRequestsAndReleases() throws InterruptedException {
        VirtualThreadMicroTaskExecutor resource = VirtualThreadMicroTaskExecutor.ofLimited(1, 0, 0, "reject-flow", false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        resource.run(() -> {
            started.countDown();
            try {
                hold.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, signal -> {
            if (signal.finallyType() == FinallyType.NOT_ACCEPT) {
                fail("占额任务不应未受理");
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS), "占额任务已启动并持有唯一资源");
        try {
            FixedResultMiddleware chain = new FixedResultMiddleware("not-used");
            RequestOrchestrator orchestrator = flowOrchestrator(chain, new ExceptionHandlerManager(Set.of()), resource);
            EmbeddedChannel channel = new EmbeddedChannel();
            FullHttpRequest request = requestWith(HttpMethod.GET, "https://a.com");

            orchestrator.orchestrate(request, new InternalHttpConnection(channel));

            assertFalse(chain.invoked().get(), "拒绝分支不进中间件链");
            FullHttpResponse written = channel.readOutbound();
            assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, written.getStatus(), "拒绝分支写出 429");
            assertEquals("too many request", written.content().toString(StandardCharsets.UTF_8), "429 正文与旧线一致");
            assertFalse(written.headers().contains("Access-Control-Allow-Origin"), "拒绝分支不经 CORS 处理");
            assertEquals(1, request.refCnt(), "拒绝分支只归还编排器自持的引用，调用方引用仍在");
            request.release();
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

    // ---------- 上下文装配：请求转化为消息与请求体内容 ----------

    /** 九组件齐备的建造者（默认通配 cors、空异常处理器表），链头由调用方给定，内容解析器管理器取内置。 */
    private static RequestOrchestrator.Builder builderWith(Middleware chain) {
        return builderWith(chain, contentParserManager());
    }

    /** 九组件齐备的建造者，链头与内容解析器管理器由调用方给定。 */
    private static RequestOrchestrator.Builder builderWith(Middleware chain, HttpContentParserManager parserManager) {
        return RequestOrchestrator.builder()
                .middleware(chain)
                .resourceManager(testResourceManager(VirtualThreadMicroTaskExecutor.ofUnlimited("test-orchestrator")))
                .responseWriterManager(new ResponseWriterManager(writerResourceManager(), List.of()))
                .resultConverterManager(new ResultConverterManager(new JacksonJsonSerializer(), List.of()))
                .corsConfig(HttpCorsConfig.builder().build())
                .exceptionHandlerManager(new ExceptionHandlerManager(Set.of()))
                .sessionManagerHolder(() -> new BackHoleSessionManager())                .defaultCharset(StandardCharsets.UTF_8)
                .contentParserManager(parserManager);
    }

    @Test
    void contextAssembledWithMessageAndParsedContent() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ContextCapturingMiddleware chain = new ContextCapturingMiddleware();
        RequestOrchestrator orchestrator = builderWith(chain).build();
        FullHttpRequest request = Requests.postJson("/submit", "{\"name\":\"alice\"}");

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        HttpContext context = chain.captured();
        assertNotNull(context, "链进入时上下文已装配");
        assertEquals(HttpRequestMethod.POST, context.message().method(), "请求方式来自请求行");
        assertEquals("/submit", context.message().requestLine().target().path(), "地址由请求转化为消息");
        assertInstanceOf(HttpJsonContent.class, context.message().content(), "JSON 请求体分派给内置 JSON 解析器");
        assertEquals("{\"name\":\"alice\"}", ((HttpJsonContent) context.message().content()).jsonString(),
                "请求体在装配上下文时已解码为字符串");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void headRequestSkipsBodyParsing() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ContextCapturingMiddleware chain = new ContextCapturingMiddleware();
        RequestOrchestrator orchestrator = builderWith(chain).build();
        FullHttpRequest request = Requests.head("/x");

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        Content content = chain.captured().message().content();
        assertInstanceOf(HttpUnknowContent.class, content, "HEAD 不进内容解析器，无请求体回落未知内容");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void invalidRequestBodyFailsAtAssemblyBeforeChain() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ContextCapturingMiddleware chain = new ContextCapturingMiddleware();
        RequestOrchestrator orchestrator = builderWith(chain).build();
        FullHttpRequest request = Requests.postJson("/submit", "not a json");

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertNull(chain.captured(), "请求体解析失败发生在装配上下文时，链不进入");
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus(),
                "解析异常与链内异常同径交异常处理器，未注册时落 500 兜底页");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void customContentParserOfInjectedManagerIsDispatched() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ContextCapturingMiddleware chain = new ContextCapturingMiddleware();
        HttpContentParserManager parserManager =
                new HttpContentParserManager(StandardCharsets.UTF_8, List.of(new MarkedContentParser()));
        RequestOrchestrator orchestrator = builderWith(chain, parserManager).build();
        FullHttpRequest request = Requests.raw(HttpMethod.GET, "/x", "text/csv", "a,b", StandardCharsets.UTF_8);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertSame(MarkedContentParser.MARKER, chain.captured().message().content(),
                "注入的管理器所持有的自定义解析器按类型分派，其产出进上下文");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void requestStaysAliveThroughChainAndSelfReferenceReleasedAfterResponseWrite() {
        // 请求生命周期的钉：链执行期间请求仍有效，表单字段经消息可读；
        // 编排器自持的引用在响应写出后的流程收尾归还，调用方引用不受影响
        EmbeddedChannel channel = new EmbeddedChannel();
        FullHttpRequest request = Requests.postForm("/submit", "name=alice");
        AtomicInteger refCntAtChain = new AtomicInteger(-1);
        AtomicReference<String> nameAtChain = new AtomicReference<>();
        Middleware chain = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                refCntAtChain.set(request.refCnt());
                Content content = ctx.message().content();
                nameAtChain.set(content instanceof HttpFormContent form
                        ? form.formParams().get("name").getFirst() : null);
                return "hello";
            }
        };
        RequestOrchestrator orchestrator = builderWith(chain).build();

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(2, refCntAtChain.get(), "链执行期间调用方引用与编排器自持引用均未被归还");
        assertEquals("alice", nameAtChain.get(), "请求体内容经消息可读");
        assertEquals(1, request.refCnt(), "响应写出后编排器归还自持引用，调用方引用仍在");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void requestIsAliveUntilResponseWrittenEvenOnChainFailure() {
        // 释放时机的钉：链内异常与兜底页写出期间请求始终有效，CORS 头按来源快照写出，
        // 响应写出后编排器才归还自持引用
        EmbeddedChannel channel = new EmbeddedChannel();
        RequestOrchestrator orchestrator = flowOrchestrator(
                new ThrowingMiddleware(new IllegalStateException("boom")), new ExceptionHandlerManager(Set.of()));
        ReleaseAwareRequest request = new ReleaseAwareRequest(HttpMethod.GET, "/guard", "https://a.com");

        ResponseWriter.WriteResult result = orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertEquals(1, request.refCnt(), "响应写出后编排器归还自持引用，调用方引用仍在");
        request.release();
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus(), "链内异常落兜底页");
        assertEquals("https://a.com", written.headers().get("Access-Control-Allow-Origin"),
                "CORS 头按来源快照写出");
        channel.finishAndReleaseAll();
    }

    @Test
    void missingConnectSessionReleasesOwnReferenceBeforeThrowing() {
        // 入口校验的钉：会话缺失在提交前拒收，不进入虚拟线程；编排入口先归还自持引用再抛出
        RequestOrchestrator orchestrator = flowOrchestrator(new SentinelMiddleware(),
                new ExceptionHandlerManager(Set.of()), new ThrowingVirtualThreadMicroTaskExecutor());
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> orchestrator.orchestrate(request, null));

        assertEquals("connect session must not be null", failure.getMessage(), "提交前拒收而非提交失败");
        assertEquals(1, request.refCnt(), "会话缺失时只归还编排器自持的引用，调用方引用不受影响");
        request.release();
    }

    @Test
    void submissionFailureReleasesOwnReferenceBeforeThrowing() {
        // 提交失败的钉：线程创建失败时任务不执行，未受理收尾先于异常抛出统一回写 429，并归还编排入口自持的引用
        EmbeddedChannel channel = new EmbeddedChannel();
        RequestOrchestrator orchestrator = flowOrchestrator(new SentinelMiddleware(),
                new ExceptionHandlerManager(Set.of()), new ThrowingVirtualThreadMicroTaskExecutor());
        FullHttpRequest request = requestWith(HttpMethod.GET, null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> orchestrator.orchestrate(request, new InternalHttpConnection(channel)));

        assertEquals("thread start failed", failure.getMessage(), "提交失败原样抛出");
        FullHttpResponse rejected = channel.readOutbound();
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, rejected.getStatus(),
                "未受理统一按拒绝处理，先于异常抛出回写 429");
        assertEquals(1, request.refCnt(), "提交失败时只归还编排器自持的引用，调用方引用仍在");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void closeableContentIsClosedByOrchestratorAtFinish() {
        // 释放收口归属的钉：持有资源的内容（AutoCloseContent）由编排器在请求收尾统一关闭，上下文不承载释放入口
        EmbeddedChannel channel = new EmbeddedChannel();
        ContextCapturingMiddleware chain = new ContextCapturingMiddleware();
        ClosableProbeContent probe = new ClosableProbeContent();
        HttpContentParserManager parserManager = new HttpContentParserManager(StandardCharsets.UTF_8,
                List.of(new FixedContentParser(probe)));
        RequestOrchestrator orchestrator = builderWith(chain, parserManager).build();
        FullHttpRequest request = Requests.raw(HttpMethod.GET, "/x", "x-test/probe", "body", StandardCharsets.UTF_8);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertTrue(probe.closed, "持有资源的内容由编排器在请求收尾关闭");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void closeableContentClosedWhenChainThrows() {
        // 异常边界的钉：内容产出后链内抛出，内容仍由编排器在同一异常边界内关闭，不随异常逃逸
        EmbeddedChannel channel = new EmbeddedChannel();
        ClosableProbeContent probe = new ClosableProbeContent();
        HttpContentParserManager parserManager = new HttpContentParserManager(StandardCharsets.UTF_8,
                List.of(new FixedContentParser(probe)));
        RequestOrchestrator orchestrator = builderWith(
                new ThrowingMiddleware(new IllegalStateException("boom")), parserManager).build();
        FullHttpRequest request = Requests.raw(HttpMethod.GET, "/x", "x-test/probe", "body", StandardCharsets.UTF_8);

        orchestrator.doOrchestrate(request, new InternalHttpConnection(channel));

        assertTrue(probe.closed, "链内异常时内容仍在编排器异常边界内关闭");
        assertEquals(1, request.refCnt(), "请求的调用方引用仍在");
        request.release();
        FullHttpResponse written = channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, written.getStatus(), "链内异常落兜底页");
        channel.finishAndReleaseAll();
    }

    /** 链成员：捕获进入的上下文并返回固定字符串。 */
    private static class ContextCapturingMiddleware extends Middleware {
        private final AtomicReference<HttpContext> captured = new AtomicReference<>();

        @Override
        public Object invoke(HttpContext ctx) {
            captured.set(ctx);
            return "hello";
        }

        HttpContext captured() {
            return captured.get();
        }
    }

    /** 释放感知请求：引用计数归零后再读取请求行或请求头即抛错，用于钉住释放发生在全部请求读取之后。 */
    private static final class ReleaseAwareRequest extends DefaultFullHttpRequest {
        ReleaseAwareRequest(HttpMethod method, String uri, String origin) {
            super(HttpVersion.HTTP_1_1, method, uri);
            if (origin != null) {
                super.headers().set("Origin", origin);
            }
        }

        private void requireAlive(String accessor) {
            if (refCnt() == 0) {
                throw new IllegalStateException("request released, but " + accessor + " was called");
            }
        }

        @Override
        public HttpMethod method() {
            requireAlive("method");
            return super.method();
        }

        @Override
        public String uri() {
            requireAlive("uri");
            return super.uri();
        }

        @Override
        public HttpHeaders headers() {
            requireAlive("headers");
            return super.headers();
        }
    }

    /** 提交即失败的虚拟线程资源：模拟线程创建失败，任务不执行，收尾以未受理情形触发后抛出。 */
    private static final class ThrowingVirtualThreadMicroTaskExecutor extends VirtualThreadMicroTaskExecutor {
        ThrowingVirtualThreadMicroTaskExecutor() {
            super("throwing-resource");
        }

        @Override
        public Thread run(Runnable task, FinallyHandler finallyHandler) {
            finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
            throw new IllegalStateException("thread start failed");
        }
    }

    /** 自定义解析器：承接 text/csv 并返回标记内容对象，用于证明注册后的分派。 */
    private static class MarkedContentParser implements HttpContentParser {

        static final Content MARKER = new Content() {
        };

        @Override
        public Set<ContentType> contentTypes() {
            return Set.of(ContentType.create("text/csv"));
        }

        @Override
        public Content parse(FullHttpRequest request, Charset defaultCharset) {
            return MARKER;
        }
    }

    /** 关闭通道探针内容：实现 AutoCloseContent，记录 close 是否被触发。 */
    private static final class ClosableProbeContent implements AutoCloseContent {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 自定义解析器：恒产出给定内容对象，供关闭时机钉用例投喂持有资源的内容。 */
    private record FixedContentParser(Content produced) implements HttpContentParser {

        @Override
        public Set<ContentType> contentTypes() {
            return Set.of(ContentType.create("x-test/probe"));
        }

        @Override
        public Content parse(FullHttpRequest request, Charset defaultCharset) {
            return produced;
        }
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
