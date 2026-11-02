package top.turboweb.http.server;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.ElasticMicroTaskExecutor;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.middleware.BranchMiddleware;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.orchestrator.RequestOrchestrator;
import top.turboweb.http.orchestrator.ResultToMessageHandler;
import top.turboweb.http.orchestrator.parser.HttpContentParser;
import top.turboweb.http.orchestrator.parser.HttpContentParserManager;
import top.turboweb.http.router.RouteHandlerManager;
import top.turboweb.http.router.RouteRegister;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.server.channel.OrchestrationChannelHandler;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.server.runtime.ResourceException;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HttpServerRuntime.Builder} 的编排器装配契约与资源管理器（spec §5 server/runtime 行）。
 *
 * <p>build() 会创建任务卸载弹性线程池并按限流参数创建虚拟线程资源，但本类每条用例都显式注入
 * 不启动 session GC 线程的 {@link NoopSessionManager}，并在断言后关闭应用；不断言任何线程终止。
 * 装配完成的编排器藏在应用持有的调度处理器里，组件身份断言经反射取证。
 */
class HttpServerRuntimeTest {

    // ---------- 构造与链式参数校验 ----------

    @Test
    void staticBuilderFactoryProducesWorkingBuilder() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpServerRuntime runtime = HttpServerRuntime.builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        assertTrue(events.contains("init:mw"), "静态工厂产出的建造者走同一装配流程，中间件在构建期完成 init");
        runtime.close();
        assertTrue(events.contains("destroy:mw"), "经静态工厂构建的应用关闭时正常销毁中间件");
    }

    @Test
    void npeWhenResourceManagerIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpServerRuntime(8, null, null));
        assertEquals("The resource manager must not be null", ex.getMessage());
    }

    @Test
    void nullArgumentSettersAreRejected() {
        HttpServerRuntime.Builder builder = new HttpServerRuntime.Builder();
        assertThrows(NullPointerException.class, () -> builder.sessionManager(null));
        assertThrows(NullPointerException.class, () -> builder.jsonSerializer(null));
        assertThrows(NullPointerException.class, () -> builder.corsConfig(null));
        assertThrows(NullPointerException.class, () -> builder.exceptionHandlers((Object) null));
        assertThrows(NullPointerException.class, () -> builder.contentParsers((HttpContentParser) null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.taskOffloadExecutor(null));
        assertThrows(NullPointerException.class, () -> builder.middleware((Middleware) null));
        // 路由登记表传null合法：表示无路由，不创建路由调用中间件
        builder.routeRegister(null);
    }

    @Test
    void duplicateMiddlewareClassIsRejected() {
        List<String> events = new ArrayList<>();
        HttpServerRuntime.Builder builder = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(new RecordingMiddleware("a", events))
                .middleware(new RecordingMiddleware("b", events));

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(ex.getMessage().startsWith("Duplicate middleware registration: "), ex.getMessage());
    }

    // ---------- 路由调用中间件在链尾的两态 ----------

    @Test
    void noRouteRegisterLeavesChainTailAtLastMiddleware() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        assertNull(mw.getNext(), "未挂路由登记表时链尾就是最后一个业务中间件，其后无路由节点");
        runtime.close();
    }

    @Test
    void routeHandlerInvokeMiddlewareAppendedAtChainTail() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        TestRouteHandlerManager router = new TestRouteHandlerManager(events);
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .routeRegister(new TestRouteRegister(router))
                .build();

        Middleware routeMiddleware = mw.getNext();
        assertNotNull(routeMiddleware, "存在路由处理器管理器时必须创建路由调用中间件追加在链尾");
        assertEquals("RouteHandlerInvokeMiddleware", routeMiddleware.getClass().getSimpleName());
        assertNull(routeMiddleware.getNext(), "路由调用中间件位于链尾，其后不得再有节点");
        assertSame("route-result", routeMiddleware.invoke(null),
                "路由调用中间件把上下文交给管理器handle，其返回结果原样作为链结果");
        assertTrue(events.contains("handle:router"), "handle必须被路由调用中间件转交给路由处理器管理器");
        runtime.close();
    }

    // ---------- 登记表在生产时刻收到应用的序列化器 ----------

    @Test
    void routeRegisterIsProducedWithRuntimeSerializer() {
        List<String> events = new ArrayList<>();
        JsonSerializer serializer = new JacksonJsonSerializer();
        TestRouteRegister register = new TestRouteRegister(new TestRouteHandlerManager(events));
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .jsonSerializer(serializer)
                .routeRegister(register)
                .build();

        assertSame(serializer, register.producedWith, "构建期把应用的json序列化器交给登记表生产管理器");
        runtime.close();
    }

    @Test
    void routeRegisterReceivesDefaultSerializerWhenNotPassed() throws Exception {
        List<String> events = new ArrayList<>();
        TestRouteRegister register = new TestRouteRegister(new TestRouteHandlerManager(events));
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .routeRegister(register)
                .build();

        assertInstanceOf(JacksonJsonSerializer.class, register.producedWith, "未指定序列化器时生产用应用的默认实例");
        ResultToMessageHandler resultToMessageHandler =
                (ResultToMessageHandler) componentOf(orchestratorOf(runtime), "resultToMessageHandler");
        assertSame(fieldOf(resultToMessageHandler, "jsonSerializer"), register.producedWith,
                "登记表拿到的默认序列化器与结果转化处理器持有的是同一实例");
        runtime.close();
    }

    // ---------- 资源作用域：登记顺序即释放顺序 ----------

    @Test
    void closeDestroysMiddlewareChainBeforePoolShutdown() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        PoolProbeMiddleware probe = new PoolProbeMiddleware(events);
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw, probe)
                .build();

        MicroTaskExecutor pool = runtime.getResourceManager().getBuiltinResource(ResourceNames.TASK_OFFLOAD_THREAD_POOL, MicroTaskExecutor.class);
        probe.attachPool(pool);
        events.clear();

        runtime.close();

        // 中间件链销毁适配器先于线程池登记，因此 probe 在自己的 destroy 里应看到池还未关闭
        assertEquals(List.of("destroy:mw", "destroy:probe:poolShutdown=false"), events,
                "销毁顺序必须是：中间件链先销毁，线程池后关闭");
        assertTrue(probe.submitRejectedAfterClose(), "应用关闭后线程池必须不再受理提交");
    }

    @Test
    void taskOffloadPoolRegisteredAsElasticMicroTaskExecutor() throws Exception {
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .build();

        MicroTaskExecutor offloadPool = runtime.getResourceManager()
                .getBuiltinResource(ResourceNames.TASK_OFFLOAD_THREAD_POOL, MicroTaskExecutor.class);

        assertInstanceOf(ElasticMicroTaskExecutor.class, offloadPool,
                "任务卸载线程池以弹性微任务执行器装配");
        runtime.close();
    }

    @Test
    void diskIoThreadPoolIsNoLongerRegistered() {
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .build();

        assertThrows(ResourceException.class,
                () -> runtime.getResourceManager().getBuiltinResource("diskIoThreadPool"),
                "磁盘IO线程池的消费方已随旧响应体系下架，装配不再登记");
        runtime.close();
    }

    // ---------- 任务卸载执行器可配置 ----------

    @Test
    void userOffloadExecutorIsRegisteredUnderResourceName() {
        DirectOffloadExecutor executor = new DirectOffloadExecutor();
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .taskOffloadExecutor(executor)
                .build();

        assertSame(executor, runtime.getResourceManager()
                        .getBuiltinResource(ResourceNames.TASK_OFFLOAD_THREAD_POOL, MicroTaskExecutor.class),
                "用户传入的执行器按资源名登记进内置空间，写出处理器按名取用的即是它");
        runtime.close();
    }

    @Test
    void runtimeCloseReleasesUserOffloadExecutor() {
        DirectOffloadExecutor executor = new DirectOffloadExecutor();
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .taskOffloadExecutor(executor)
                .build();

        runtime.close();

        assertTrue(executor.closed, "任务卸载执行器是系统内置资源，传入的实例由框架随应用关闭统一释放");
    }

    // ---------- 装配后中间件链被锁定 ----------

    @Test
    void lockMiddlewareMakesSetNextInert() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware a = new RecordingMiddleware("a", events);
        RecordingMiddleware b = new RecordingMiddleware("b", events);
        RecordingMiddleware c = new RecordingMiddleware("c", events);
        a.setNext(b);
        a.lockMiddleware();

        a.setNext(c);

        assertSame(b, a.getNext(), "锁定后 setNext 只告警，不改链");
    }

    @Test
    void buildLocksEveryMiddlewareSoLaterSetNextIsIgnored() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        mw.setNext(new RecordingMiddleware("intruder", events));

        assertNull(mw.getNext(), "build() 已锁定全部中间件，装配后再 setNext 无效");
        runtime.close();
    }

    // ---------- 装配管道（configure） ----------

    @Test
    void configureAppliesInFlightLimitFromConfig() throws Exception {
        // 链内挂起钉住在途状态：前两个请求停在中间件链内不写出响应，写出游标停在 1，
        // 第三个请求的派发判定必然按在途 2 超限断连，不受异步响应完成时序干扰
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        Middleware holdingChain = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                entered.countDown();
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "held";
            }
        };
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(holdingChain)
                .config(HttpConfig.builder().maxInFlightResponses(2).build())
                .build();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            runtime.configure(channel.pipeline());
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/1"));
            assertTrue(entered.await(2, TimeUnit.SECONDS), "首个请求已进入中间件链并在链内挂起");
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/2"));
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/3"));

            channel.runPendingTasks();
            assertFalse(channel.isOpen(), "在途请求数超过配置的上限即断开连接");
            channel.finishAndReleaseAll();
        } finally {
            hold.countDown();
            runtime.close();
        }
    }

    // ---------- 全量 setter 直通编排器 ----------

    @Test
    void allSettersAreAppliedDuringBuild() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        TestRouteHandlerManager router = new TestRouteHandlerManager(events);
        JsonSerializer serializer = new JacksonJsonSerializer();
        HttpCorsConfig corsConfig = HttpCorsConfig.builder().build();
        MarkerExceptionHandler customHandler = new MarkerExceptionHandler();
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .exceptionHandlers(customHandler)
                .sessionManager(new NoopSessionManager())
                .routeRegister(new TestRouteRegister(router))
                .jsonSerializer(serializer)
                .corsConfig(corsConfig)
                .config(HttpConfig.builder().maxInFlightResponses(16).build())
                .middleware(mw)
                .build();

        RequestOrchestrator orchestrator = orchestratorOf(runtime);
        assertSame(corsConfig, componentOf(orchestrator, "corsConfig"),
                "用户传入的cors配置原样进入编排器");
        ExceptionHandlerManager exceptionHandlerManager =
                (ExceptionHandlerManager) componentOf(orchestrator, "exceptionHandlerManager");
        assertNotNull(exceptionHandlerManager, "异常处理器管理器由构建期装配进入编排器");
        Map<?, ?> handlerTable = (Map<?, ?>) fieldOf(exceptionHandlerManager, "handlers");
        assertTrue(handlerTable.containsKey(IllegalArgumentException.class),
                "用户传入的异常处理器经构建期装配登记进管理器的异常类型表");
        ResultToMessageHandler resultToMessageHandler =
                (ResultToMessageHandler) componentOf(orchestrator, "resultToMessageHandler");
        assertNotNull(resultToMessageHandler, "结果转化处理器由构建期装配进入编排器");
        Middleware head = (Middleware) componentOf(orchestrator, "middleware");
        assertInstanceOf(SentinelMiddleware.class, head, "编排器的中间件组件是链头哨兵");
        assertSame(mw, head.getNext(), "业务中间件紧随哨兵");
        assertEquals("RouteHandlerInvokeMiddleware", mw.getNext().getClass().getSimpleName(),
                "路由调用中间件追加在链尾");
        assertNull(mw.getNext().getNext(), "路由调用中间件之后无节点");
        runtime.close();
    }

    @Test
    void customContentParsersAreRegisteredIntoManager() throws Exception {
        MarkerContentParser parser = new MarkerContentParser();
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .contentParsers(parser)
                .build();

        HttpContentParserManager manager = (HttpContentParserManager)
                componentOf(orchestratorOf(runtime), "contentParserManager");
        Map<?, ?> customParsers = (Map<?, ?>) fieldOf(manager, "customParsers");
        assertSame(parser, customParsers.get("text/csv"),
                "用户传入的自定义内容解析器经构建期装配进入管理器的自定义表");
        runtime.close();
    }

    @Test
    void unpassedComponentsFallBackToDefaults() throws Exception {
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .build();
        RequestOrchestrator orchestrator = orchestratorOf(runtime);
        for (String component : List.of("middleware", "microTaskExecutor", "responseMessageWriter",
                "resultToMessageHandler", "corsConfig", "exceptionHandlerManager",
                "sessionManagerHolder", "defaultCharset", "contentParserManager")) {
            assertNotNull(componentOf(orchestrator, component), "九组件之 " + component + " 装配后不可为null");
        }
        assertEquals(Integer.MAX_VALUE,
                ((VirtualThreadMicroTaskExecutor) componentOf(orchestrator, "microTaskExecutor")).availableResources(),
                "默认配置未启用限流，非受限实现不设资源上限");
        assertFalse(((VirtualThreadMicroTaskExecutor) componentOf(orchestrator, "microTaskExecutor")).isLimited(),
                "默认配置未启用限流，虚拟线程资源为非受限实现");
        assertTrue(((HttpCorsConfig) componentOf(orchestrator, "corsConfig")).isAnyOrigin(),
                "cors配置默认取通配配置");
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, componentOf(orchestrator, "defaultCharset"),
                "缺省字符集默认取UTF-8并装入编排器");
        runtime.close();
    }

    @Test
    void defaultCharsetIsCarriedFromConfig() throws Exception {
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .config(HttpConfig.builder().defaultCharset(java.nio.charset.Charset.forName("GBK")).build())
                .build();
        try {
            assertEquals(java.nio.charset.Charset.forName("GBK"),
                    componentOf(orchestratorOf(runtime), "defaultCharset"),
                    "编排器缺省字符集取自 HttpConfig 的装配值");
        } finally {
            runtime.close();
        }
    }

    // ---------- 虚拟线程资源按配置创建 ----------

    @Test
    void virtualThreadResourceIsCreatedFromConfig() throws Exception {
        HttpServerRuntime limited = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .config(HttpConfig.builder()
                        .enableHttpSchedulerLimit(true)
                        .httpSchedulerLimitCount(2)
                        .build())
                .build();
        VirtualThreadMicroTaskExecutor resource =
                (VirtualThreadMicroTaskExecutor) componentOf(orchestratorOf(limited), "microTaskExecutor");
        assertTrue(resource.isLimited(), "启用限流时应用按配置创建受限虚拟线程资源");
        limited.close();
    }

    // ---------- 缺陷候选实测裁决 ----------

    @Test
    @DisplayName("F5 回归：同一 Builder 重复 build() 抛出 IllegalStateException")
    void secondBuildOnSameBuilderIsRejected() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpServerRuntime.Builder builder = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw);

        HttpServerRuntime first = builder.build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build,
                "F5 修复回归：Builder 以原子布尔一次性门拒绝重复 build");
        assertEquals("build() can only be called once on the same Builder", ex.getMessage());

        assertEquals(List.of("init:mw"), events,
                "第二次 build 在进入装配前即被拒绝，未对同一中间件二次 init");
        first.close();
        assertEquals(List.of("init:mw", "destroy:mw"), events,
                "首个应用正常销毁其唯一中间件实例");
    }

    @Test
    @DisplayName("F1 回归：关闭应用时分支内中间件随分支链一并销毁")
    void branchMiddlewareInsideBranchIsDestroyedOnClose() {
        List<String> events = new ArrayList<>();
        TestBranchMiddleware owner = new TestBranchMiddleware();
        RecordingMiddleware branchMw = new RecordingMiddleware("branch", events);
        owner.addMiddleware("b", branchMw);
        RecordingMiddleware mainMw = new RecordingMiddleware("main", events);

        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(owner, mainMw)
                .build();

        assertTrue(events.contains("init:branch"), "分支中间件在装配期被 init，说明它已进入分支链");
        events.clear();

        runtime.close();

        assertTrue(events.contains("destroy:main"), "主链上的中间件被销毁");
        assertTrue(events.contains("destroy:branch"),
                "F1 修复回归：BranchMiddleware 覆写 destroy 后，分支内中间件随分支链销毁、不再漏于主链遍历");
    }

    // ---------- 夹具 ----------

    /**
     * 同步直执行的卸载执行器夹具：submit 在提交线程直接执行任务并以 EXECUTED 收尾，
     * close 只记录调用不做任何动作。用于钉住传入执行器的登记与框架统一释放口径。
     */
    private static final class DirectOffloadExecutor implements MicroTaskExecutor {

        private boolean closed = false;

        @Override
        public void submit(Runnable task, FinallyHandler finallyHandler) {
            task.run();
            finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.EXECUTED));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * 取出应用装配完成的请求编排器：应用持有调度处理器、处理器持有编排器，两级均为私有字段，
     * L1 只能反射读取。字段一旦改名，相关用例会以
     * {@code NoSuchFieldException} 变红，届时同步更新即可。
     */
    private static RequestOrchestrator orchestratorOf(HttpServerRuntime runtime) throws Exception {
        Field handlerField = HttpServerRuntime.class.getDeclaredField("httpRequestSchedulerHandler");
        handlerField.setAccessible(true);
        Object handler = handlerField.get(runtime);
        Field orchestratorField = OrchestrationChannelHandler.class.getDeclaredField("requestOrchestrator");
        orchestratorField.setAccessible(true);
        return (RequestOrchestrator) orchestratorField.get(handler);
    }

    /** 反射读取编排器指定名称的私有组件字段值。 */
    private static Object componentOf(RequestOrchestrator orchestrator, String fieldName) throws Exception {
        Field field = RequestOrchestrator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(orchestrator);
    }

    /** 反射读取目标对象实际类上指定名称的私有字段值（与 componentOf 同一类取证手段）。 */
    private static Object fieldOf(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    /** 链尾探测中间件：在 destroy 时以一次空提交探测内置线程池执行器的受理状态，用于断言释放顺序。 */
    private static final class PoolProbeMiddleware extends Middleware {

        private final List<String> events;
        private MicroTaskExecutor pool;

        PoolProbeMiddleware(List<String> events) {
            this.events = events;
        }

        void attachPool(MicroTaskExecutor pool) {
            this.pool = pool;
        }

        /** 应用关闭后再次提交必须未受理：返回 true 表示提交被拒（执行器已关闭）。 */
        boolean submitRejectedAfterClose() {
            return !submitProbe();
        }

        /** 以一次空提交探测执行器受理状态：受理返回 true，未受理（已关闭）返回 false。 */
        private boolean submitProbe() {
            AtomicBoolean accepted = new AtomicBoolean(true);
            pool.submit(() -> { }, signal -> {
                if (signal.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT) {
                    accepted.set(false);
                }
            });
            return accepted.get();
        }

        @Override
        public Object invoke(HttpContext ctx) {
            return next(ctx);
        }

        @Override
        public void destroy() {
            events.add("destroy:probe:poolShutdown=" + !submitProbe());
        }
    }

    /**
     * 记录生产时刻收到的 json 序列化器并返回给定管理器的路由登记表：
     * 用于断言应用在构建期把自家序列化器交给登记表生产，而不是让调用方自备成品。
     */
    private static final class TestRouteRegister implements RouteRegister {

        private final RouteHandlerManager manager;

        private JsonSerializer producedWith;

        TestRouteRegister(RouteHandlerManager manager) {
            this.manager = manager;
        }

        @Override
        public RouteHandlerManager newRouteHandlerManager(JsonSerializer jsonSerializer) {
            this.producedWith = jsonSerializer;
            return manager;
        }
    }

    /** 记录 handle 调用并返回固定标记的路由处理器管理器，用于断言链尾包装与直通。 */
    private static final class TestRouteHandlerManager implements RouteHandlerManager {

        private final List<String> events;

        TestRouteHandlerManager(List<String> events) {
            this.events = events;
        }

        @Override
        public Object handle(HttpContext context) {
            events.add("handle:router");
            return "route-result";
        }
    }

    /** 固定返回分支键 "b" 的分支中间件，用于驱动 F1 的分支链装配。 */
    private static final class TestBranchMiddleware extends BranchMiddleware {

        @Override
        protected String getBranchKey(HttpContext ctx) {
            return "b";
        }
    }

    /**
     * 自定义内容解析器夹具：只声明 text/csv 类型，parse 不被调用（用例只核查构建期登记结果），
     * 用于钉住用户传入的自定义内容解析器经构建期装配进入内容解析器管理器的自定义表。
     */
    private static final class MarkerContentParser implements HttpContentParser {

        @Override
        public Set<ContentType> contentTypes() {
            return Set.of(ContentType.create("text/csv"));
        }

        @Override
        public Object parse(FullHttpRequest request, Charset defaultCharset) {
            throw new AssertionError("marker parser must not be invoked during assembly");
        }
    }

    /**
     * 异常处理器夹具：声明一个 IllegalArgumentException 的处理方法（用例只核查构建期登记结果，
     * 方法不被执行）；管理器按签名反射认领方法，故须为公有类的公有方法。
     * 用于钉住用户传入的异常处理器经构建期装配登记进管理器的异常类型表。
     */
    public static class MarkerExceptionHandler {

        public String handle(IllegalArgumentException e) {
            return "handled";
        }
    }

    /**
     * 空实现的 session 管理器：{@code sessionGC} 不启动任何后台线程，
     * 会话安全作用域直接透传回调，使 L1 用例不触碰 session GC 线程与互斥等待。
     */
    private static final class NoopSessionManager implements SessionManager {

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
        public void sessionGC(long checkTime, long maxNotUseTime, long checkForSessionNums) {
        }

        @Override
        public String sessionManagerName() {
            return "noop session manager";
        }

        @Override
        public void expireAt(String sessionId) {
        }

        @Override
        public <T> T sessionSafeScope(Function<SessionManager, T> action) {
            return action.apply(this);
        }
    }
}
