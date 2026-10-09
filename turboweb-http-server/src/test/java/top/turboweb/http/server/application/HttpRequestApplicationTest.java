package top.turboweb.http.server.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.utils.resource.ScopedResource;
import top.turboweb.commons.utils.thread.ThreadPool;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.BranchMiddleware;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.processor.convertor.DefaultHttpResponseConverter;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpRequestApplication.Builder} 的装配契约与资源作用域（spec §5 server/application 行）。
 *
 * <p>build() 会创建 ElasticThreadPool（搬运虚拟线程），但本类每条用例都显式注入不启动
 * session GC 线程的 {@link NoopSessionManager}，并在断言后关闭应用；不断言任何线程终止。
 */
class HttpRequestApplicationTest {

    // ---------- 构造与链式参数校验 ----------

    @Test
    void npeWhenScopedResourceIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpRequestApplication(false, null, null));
        assertEquals("资源作用域不能为null", ex.getMessage());
    }

    @Test
    void nullArgumentSettersAreRejected() {
        HttpRequestApplication.Builder builder = new HttpRequestApplication.Builder();
        assertThrows(NullPointerException.class, () -> builder.sessionManager(null));
        assertThrows(NullPointerException.class, () -> builder.jsonSerializer(null));
        assertThrows(NullPointerException.class, () -> builder.cors(null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.middleware((Middleware) null));
        assertThrows(NullPointerException.class, () -> builder.exceptionHandler((Object) null));
    }

    @Test
    void duplicateMiddlewareClassIsRejected() {
        List<String> events = new ArrayList<>();
        HttpRequestApplication.Builder builder = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(new RecordingMiddleware("a", events))
                .middleware(new RecordingMiddleware("b", events));

        IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(ex.getMessage().startsWith("中间件重复注册:"), ex.getMessage());
    }

    // ---------- 路由管理器在链尾的三态 ----------

    @Test
    void routerManagerNullLeavesChainTailAtLastMiddleware() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .build();

        assertNull(mw.getNext(), "routerManager==null 时链尾就是最后一个业务中间件，其后无路由节点");
        app.close();
    }

    @Test
    void routerManagerIsAppendedAtChainTail() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        TestRouterManager router = new TestRouterManager(events);
        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .sessionManager(new NoopSessionManager())
                .middleware(mw)
                .routerManager(router)
                .build();

        assertSame(router, mw.getNext(), "路由管理器追加在最后一个业务中间件之后");
        assertNull(router.getNext(), "路由管理器位于链尾，其后不得再有节点");
        app.close();
        assertTrue(events.contains("destroy:router"), "链尾的路由管理器也必须被销毁");
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

    // ---------- 全量 setter 生效 + cors 消费者确实被调用 ----------

    @Test
    void allSettersAreAppliedDuringBuild() {
        List<String> events = new ArrayList<>();
        AtomicBoolean corsApplied = new AtomicBoolean(false);
        RecordingMiddleware mw = new RecordingMiddleware("mw", events);
        TestRouterManager router = new TestRouterManager(events);

        HttpRequestApplication app = new HttpRequestApplication.Builder()
                .exceptionHandler(new Object())
                .sessionManager(new NoopSessionManager())
                .routerManager(router)
                .jsonSerializer(new JacksonJsonSerializer())
                .responseConverter(new DefaultHttpResponseConverter(new JacksonJsonSerializer()))
                .cors(config -> corsApplied.set(true))
                .config(HttpConfig.builder().serializePerConnection(true).build())
                .middleware(mw)
                .build();

        assertTrue(corsApplied.get(), "build() 必须把 cors 消费者作用到 CorsProcessor.Config 上");
        assertSame(router, mw.getNext());
        app.close();
        assertTrue(events.contains("destroy:router"));
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
        assertEquals("Builder 已构建过应用，不能重复 build()", ex.getMessage());

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
     * 从应用资源作用域里取出磁盘IO线程池：{@code ScopedResource} 不暴露已登记资源，
     * L1 只能反射读它私有的 {@code registedResource}（与 commons 阶段读 {@code GlobalConfig}
     * 私有静态的手法是同一类取证手段）。字段一旦改名，本用例会以
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

    /** 记录 init/destroy 的测试路由管理器，仅用于断言它在链尾及其销毁。 */
    private static final class TestRouterManager extends RouterManager {

        private final List<String> events;

        TestRouterManager(List<String> events) {
            this.events = events;
        }

        @Override
        protected RouterContainer getRouterContainer() {
            return null;
        }

        @Override
        public void init(Middleware chain) {
            super.init(chain);
            events.add("init:router");
        }

        @Override
        public void destroy() {
            events.add("destroy:router");
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
     * 空实现的 session 管理器：{@code sessionGC} 不启动任何后台线程，使 L1 用例不触碰
     * JVM 级的 {@code Locks.SESSION_LOCK} 与 session GC 线程。
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
    }
}
