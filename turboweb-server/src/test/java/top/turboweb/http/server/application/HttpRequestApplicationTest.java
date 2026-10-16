package top.turboweb.http.server.application;

import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.common.utils.resource.ScopedResource;
import top.turboweb.concurrent.thread.LimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.concurrent.thread.UnlimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.VirtualThreadResource;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.middleware.BranchMiddleware;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.orchestrator.RequestOrchestrator;
import top.turboweb.http.orchestrator.ResponseWriter;
import top.turboweb.http.orchestrator.ResponseWriterManager;
import top.turboweb.http.orchestrator.ResultConverter;
import top.turboweb.http.orchestrator.ResultConverterManager;
import top.turboweb.http.router.RouteHandlerManager;
import top.turboweb.http.router.RouteRegister;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.server.handler.HttpRequestSchedulerChannelHandler;
import top.turboweb.http.core.session.SessionManager;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpRequestApplication.Builder} 的编排器装配契约与资源作用域（spec §5 server/application 行）。
 *
 * <p>build() 会创建磁盘IO弹性线程池并按限流参数创建虚拟线程资源，但本类每条用例都显式注入
 * 不启动 session GC 线程的 {@link NoopSessionManager}，并在断言后关闭应用；不断言任何线程终止。
 * 装配完成的编排器藏在应用持有的调度处理器里，组件身份断言经反射取证（与 {@code diskPoolOf}
 * 同一类取证手段）。
 */
class HttpRequestApplicationTest {

    // ---------- 构造与链式参数校验 ----------

    @Test
    void npeWhenScopedResourceIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpRequestApplication(false, null, null));
        assertEquals("The resource scope must not be null", ex.getMessage());
    }

    @Test
    void nullArgumentSettersAreRejected() {
        HttpRequestApplication.Builder builder = new HttpRequestApplication.Builder();
        assertThrows(NullPointerException.class, () -> builder.sessionManager(null));
        assertThrows(NullPointerException.class, () -> builder.jsonSerializer(null));
        assertThrows(NullPointerException.class, () -> builder.corsConfig(null));
        assertThrows(NullPointerException.class, () -> builder.exceptionHandlers((Object) null));
        assertThrows(NullPointerException.class, () -> builder.resultConverters((ResultConverter) null));
        assertThrows(NullPointerException.class, () -> builder.responseWriters((ResponseWriter) null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.middleware((Middleware) null));
        // 路由登记表传null合法：表示无路由，不创建路由调用中间件
        builder.routeRegister(null);
    }

    @Test
    void duplicateMiddlewareClassIsRejected() {
        List<String> events = new ArrayList<>();
        HttpRequestApplication.Builder builder = new HttpRequestApplication.Builder()
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
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        assertNull(mw.getNext(), "未挂路由登记表时链尾就是最后一个业务中间件，其后无路由节点");
        app.close();
    }

    @Test
    void routeHandlerInvokeMiddlewareAppendedAtChainTail() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        TestRouteHandlerManager router = new TestRouteHandlerManager(events);
        HttpRequestApplication app = new HttpRequestApplication.Builder()
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
        app.close();
    }

    // ---------- 登记表在生产时刻收到应用的序列化器 ----------

    @Test
    void routeRegisterIsProducedWithApplicationSerializer() {
        List<String> events = new ArrayList<>();
        JsonSerializer serializer = new JacksonJsonSerializer();
        TestRouteRegister register = new TestRouteRegister(new TestRouteHandlerManager(events));
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .jsonSerializer(serializer)
                .routeRegister(register)
                .build();

        assertSame(serializer, register.producedWith, "构建期把应用的json序列化器交给登记表生产管理器");
        app.close();
    }

    @Test
    void routeRegisterReceivesDefaultSerializerWhenNotPassed() throws Exception {
        List<String> events = new ArrayList<>();
        TestRouteRegister register = new TestRouteRegister(new TestRouteHandlerManager(events));
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .routeRegister(register)
                .build();

        assertInstanceOf(JacksonJsonSerializer.class, register.producedWith, "未指定序列化器时生产用应用的默认实例");
        assertSame(componentOf(orchestratorOf(app), "jsonSerializer"), register.producedWith,
                "登记表拿到的默认序列化器与编排器持有的是同一实例");
        app.close();
    }

    // ---------- 资源作用域：登记顺序即释放顺序 ----------

    @Test
    void closeDestroysMiddlewareChainBeforeDiskPoolShutdown() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        DiskPoolProbeMiddleware probe = new DiskPoolProbeMiddleware(events);
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw, probe)
                .build();

        ThreadPool diskPool = diskPoolOf(app.getScopedResource());
        probe.attachDiskPool(diskPool);
        assertFalse(diskPool.isShutdown(), "构建后磁盘池尚未关闭");
        events.clear();

        app.close();

        // 中间件链销毁适配器先于磁盘池登记，因此 probe 在自己的 destroy 里应看到池还未 shutdown
        assertEquals(List.of("destroy:mw", "destroy:probe:poolShutdown=false"), events,
                "销毁顺序必须是：中间件链先销毁，磁盘池后关闭");
        assertTrue(diskPool.isShutdown(), "应用关闭后磁盘池必须已 shutdown");
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
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        mw.setNext(new RecordingMiddleware("intruder", events));

        assertNull(mw.getNext(), "build() 已锁定全部中间件，装配后再 setNext 无效");
        app.close();
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
        ResultConverter customConverter = (result, charset) -> ResultConverter.Result.shouldContinue();
        MarkerResponseWriter customWriter = new MarkerResponseWriter();
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .exceptionHandlers(customHandler)
                .sessionManager(new NoopSessionManager())
                .routeRegister(new TestRouteRegister(router))
                .jsonSerializer(serializer)
                .resultConverters(customConverter)
                .responseWriters(customWriter)
                .corsConfig(corsConfig)
                .config(HttpConfig.builder().serializePerConnection(true).build())
                .middleware(mw)
                .build();

        RequestOrchestrator orchestrator = orchestratorOf(app);
        assertSame(corsConfig, componentOf(orchestrator, "corsConfig"),
                "用户传入的cors配置原样进入编排器");
        ExceptionHandlerManager exceptionHandlerManager =
                (ExceptionHandlerManager) componentOf(orchestrator, "exceptionHandlerManager");
        assertNotNull(exceptionHandlerManager, "异常处理器管理器由构建期装配进入编排器");
        Map<?, ?> handlerTable = (Map<?, ?>) fieldOf(exceptionHandlerManager, "handlers");
        assertTrue(handlerTable.containsKey(IllegalArgumentException.class),
                "用户传入的异常处理器经构建期装配登记进管理器的异常类型表");
        ResultConverterManager resultConverterManager =
                (ResultConverterManager) componentOf(orchestrator, "resultConverterManager");
        assertNotNull(resultConverterManager, "结果转化器管理器由构建期装配进入编排器");
        List<?> customConverters = (List<?>) fieldOf(resultConverterManager, "customConverters");
        assertSame(customConverter, customConverters.get(0),
                "用户传入的自定义结果转化器经构建期装配进入管理器的自定义环节");
        ResponseWriterManager responseWriterManager =
                (ResponseWriterManager) componentOf(orchestrator, "responseWriterManager");
        assertNotNull(responseWriterManager, "响应写出器管理器由构建期装配进入编排器");
        Map<?, ?> customWriters = (Map<?, ?>) fieldOf(responseWriterManager, "customerResponseWriter");
        assertSame(customWriter, customWriters.get(customWriter.getClass()),
                "用户传入的自定义响应写出器经构建期装配进入管理器的自定义表");
        assertSame(serializer, componentOf(orchestrator, "jsonSerializer"),
                "用户传入的json序列化器原样进入编排器");
        Middleware head = (Middleware) componentOf(orchestrator, "middleware");
        assertInstanceOf(SentinelMiddleware.class, head, "编排器的中间件组件是链头哨兵");
        assertSame(mw, head.getNext(), "业务中间件紧随哨兵");
        assertEquals("RouteHandlerInvokeMiddleware", mw.getNext().getClass().getSimpleName(),
                "路由调用中间件追加在链尾");
        assertNull(mw.getNext().getNext(), "路由调用中间件之后无节点");
        app.close();
    }

    @Test
    void unpassedComponentsFallBackToDefaults() throws Exception {
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .build();
        RequestOrchestrator orchestrator = orchestratorOf(app);
        for (String component : List.of("middleware", "virtualThreadResource", "responseWriterManager",
                "resultConverterManager", "corsConfig", "exceptionHandlerManager",
                "sessionManagerHolder", "jsonSerializer", "defaultCharset", "contentParserManager")) {
            assertNotNull(componentOf(orchestrator, component), "十组件之 " + component + " 装配后不可为null");
        }
        assertInstanceOf(UnlimitedVirtualThreadResource.class,
                componentOf(orchestrator, "virtualThreadResource"),
                "默认配置未启用限流，虚拟线程资源为非受限实现");
        assertTrue(((HttpCorsConfig) componentOf(orchestrator, "corsConfig")).isAnyOrigin(),
                "cors配置默认取通配配置");
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, componentOf(orchestrator, "defaultCharset"),
                "缺省字符集默认取UTF-8并装入编排器");
        app.close();
    }

    @Test
    void defaultCharsetIsCarriedFromConfig() throws Exception {
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .config(HttpConfig.builder().defaultCharset(java.nio.charset.Charset.forName("GBK")).build())
                .build();
        try {
            assertEquals(java.nio.charset.Charset.forName("GBK"),
                    componentOf(orchestratorOf(app), "defaultCharset"),
                    "编排器缺省字符集取自 HttpConfig 的装配值");
        } finally {
            app.close();
        }
    }

    // ---------- 虚拟线程资源按配置创建 ----------

    @Test
    void virtualThreadResourceIsCreatedFromConfig() throws Exception {
        HttpRequestApplication limited = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .config(HttpConfig.builder()
                        .enableHttpSchedulerLimit(true)
                        .httpSchedulerLimitCount(2)
                        .build())
                .build();
        VirtualThreadResource resource =
                (VirtualThreadResource) componentOf(orchestratorOf(limited), "virtualThreadResource");
        assertInstanceOf(LimitedVirtualThreadResource.class, resource,
                "启用限流时应用按配置创建受限虚拟线程资源");
        limited.close();
    }

    // ---------- 缺陷候选实测裁决 ----------

    @Test
    @DisplayName("F5 回归：同一 Builder 重复 build() 抛出 IllegalStateException")
    void secondBuildOnSameBuilderIsRejected() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpRequestApplication.Builder builder = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw);

        HttpRequestApplication first = builder.build();

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

        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(owner, mainMw)
                .build();

        assertTrue(events.contains("init:branch"), "分支中间件在装配期被 init，说明它已进入分支链");
        events.clear();

        app.close();

        assertTrue(events.contains("destroy:main"), "主链上的中间件被销毁");
        assertTrue(events.contains("destroy:branch"),
                "F1 修复回归：BranchMiddleware 覆写 destroy 后，分支内中间件随分支链销毁、不再漏于主链遍历");
    }

    // ---------- 夹具 ----------

    /**
     * 取出应用装配完成的请求编排器：应用持有调度处理器、处理器持有编排器，两级均为私有字段，
     * L1 只能反射读取（与 {@code diskPoolOf} 同一类取证手段）。字段一旦改名，相关用例会以
     * {@code NoSuchFieldException} 变红，届时同步更新即可。
     */
    private static RequestOrchestrator orchestratorOf(HttpRequestApplication app) throws Exception {
        Field handlerField = HttpRequestApplication.class.getDeclaredField("httpRequestSchedulerHandler");
        handlerField.setAccessible(true);
        Object handler = handlerField.get(app);
        Field orchestratorField = HttpRequestSchedulerChannelHandler.class.getDeclaredField("requestOrchestrator");
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

    /**
     * 从应用资源作用域里取出磁盘IO线程池：{@code ScopedResource} 不暴露已登记资源，
     * L1 只能反射读它私有的 {@code registedResource}（反射私有成员是同一类取证手段）。
     * 字段一旦改名，本用例会以
     * {@code NoSuchFieldException} 变红，届时同步更新即可。
     */
    @SuppressWarnings("unchecked")
    private static ThreadPool diskPoolOf(ScopedResource scope) throws Exception {
        Field field = ScopedResource.class.getDeclaredField("registedResource");
        field.setAccessible(true);
        Set<AutoCloseable> resources = (Set<AutoCloseable>) field.get(scope);
        return resources.stream()
                .filter(ThreadPool.class::isInstance)
                .map(ThreadPool.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("资源作用域中没有登记磁盘IO线程池"));
    }

    /** 链尾探测中间件：在 destroy 时记录磁盘池的 shutdown 状态，用于断言释放顺序。 */
    private static final class DiskPoolProbeMiddleware extends Middleware {

        private final List<String> events;
        private ThreadPool diskPool;

        DiskPoolProbeMiddleware(List<String> events) {
            this.events = events;
        }

        void attachDiskPool(ThreadPool diskPool) {
            this.diskPool = diskPool;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            return next(ctx);
        }

        @Override
        public void destroy() {
            events.add("destroy:probe:poolShutdown=" + diskPool.isShutdown());
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
     * 自定义写出器夹具：线程池注入为空操作、写出恒不被调用（用例只核查构建期登记结果），
     * 用于钉住用户传入的自定义写出器经构建期装配进入响应写出器管理器的自定义表。
     */
    private static final class MarkerResponseWriter implements ResponseWriter {

        @Override
        public void injectThreadPool(ThreadPool threadPool) {
        }

        @Override
        public WriteResult write(HttpResponse httpResponse, InternalConnectSession session) {
            return WriteResult.notListenable();
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
