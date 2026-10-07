package top.turboweb.http.middleware.interceptor;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.http.testkit.Stubs;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InterceptorManager} 的路径模式命中、pre/post/after 三段语义与排序（spec §5）。
 * <p>
 * 顺序用 RecordingInterceptor 的调用流水证明，不靠日志。
 */
class InterceptorManagerTest {

    private static HttpContext ctx(String uri) {
        return Stubs.httpContext(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
    }

    /** 把 pre/post/after 按调用顺序写进共享 events；可注入 pre 返回值与 post 是否改写结果。 */
    private static final class RecordingInterceptor implements InterceptorHandler {

        private final String name;
        private final int order;
        private final List<String> events;
        private final boolean preResult;
        private final boolean rewrite;

        RecordingInterceptor(String name, int order, List<String> events, boolean preResult, boolean rewrite) {
            this.name = name;
            this.order = order;
            this.events = events;
            this.preResult = preResult;
            this.rewrite = rewrite;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean preHandler(HttpContext ctx) {
            events.add("pre:" + name);
            return preResult;
        }

        @Override
        public Object postHandler(HttpContext ctx, Object result) {
            events.add("post:" + name);
            return rewrite ? result + "+" + name : result;
        }

        @Override
        public void afterCompletion(Throwable exception) {
            events.add("after:" + name + (exception == null ? "" : ":err"));
        }
    }

    /** 只实现 order()，用于覆盖 InterceptorHandler 的三个 default 方法。 */
    private static final class DefaultsOnly implements InterceptorHandler {
        @Override
        public int order() {
            return 0;
        }
    }

    @Test
    void interceptorsRunByOrderAndPostRunsInReverse() {
        List<String> events = new ArrayList<>();
        InterceptorManager manager = new InterceptorManager();
        // 注册顺序与 order 顺序相反：证明最终顺序来自 order 排序，而不是注册顺序/匹配集合迭代顺序
        manager.addInterceptionHandler("/api/**", new RecordingInterceptor("A", 2, events, true, true));
        manager.addInterceptionHandler("/api/*", new RecordingInterceptor("B", 1, events, true, true));
        manager.init(null);
        manager.setNext(new RecordingMiddleware("down", events, "ok"));

        Object result = manager.invoke(ctx("/api/x?k=v"));

        assertEquals("ok+A+B", result);
        assertEquals(
                List.of("pre:B", "pre:A", "invoke:down", "post:A", "post:B", "after:A", "after:B"),
                events);
    }

    @Test
    void noMatchingInterceptorStillInvokesDownstream() {
        List<String> events = new ArrayList<>();
        InterceptorManager manager = new InterceptorManager();
        manager.addInterceptionHandler("/api/*", new RecordingInterceptor("A", 1, events, true, false));
        manager.init(null);
        manager.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", manager.invoke(ctx("/nope?x=1")));
        assertEquals(List.of("invoke:down"), events);
    }

    @Test
    void failingPreInterruptsChainAndSuppressesPostAndAfter() {
        List<String> events = new ArrayList<>();
        InterceptorManager manager = new InterceptorManager();
        manager.addInterceptionHandler("/api/*", new RecordingInterceptor("A", 1, events, true, false));
        manager.addInterceptionHandler("/api/**", new RecordingInterceptor("B", 2, events, false, false));
        manager.init(null);
        manager.setNext(new RecordingMiddleware("down", events, "ok"));

        assertNull(manager.invoke(ctx("/api/x?k=v")));
        // A 的 pre 通过（passIndex=0），B 的 pre 返回 false 中断：post 全不执行，只有 A 的 after
        assertEquals(List.of("pre:A", "pre:B", "after:A"), events);
    }

    @Test
    void exceptionFromDownstreamIsRethrownAndPassedToAfterCompletion() {
        List<String> events = new ArrayList<>();
        RuntimeException boom = new RuntimeException("boom");
        InterceptorManager manager = new InterceptorManager();
        manager.addInterceptionHandler("/api/*", new RecordingInterceptor("A", 1, events, true, false));
        manager.init(null);
        manager.setNext(new Middleware() {
            @Override
            public Object invoke(HttpContext c) {
                events.add("invoke:down");
                throw boom;
            }
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.invoke(ctx("/api/x?k=v")));
        assertSame(boom, thrown, "下游异常必须原样抛出");
        assertEquals(List.of("pre:A", "invoke:down", "after:A:err"), events);
    }

    @Test
    void trailingSlashInUriIsNormalizedBeforeMatch() {
        List<String> events = new ArrayList<>();
        InterceptorManager manager = new InterceptorManager();
        manager.addInterceptionHandler("/api/*", new RecordingInterceptor("A", 1, events, true, false));
        manager.init(null);
        manager.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", manager.invoke(ctx("/api/x/?k=v")));
        assertEquals(List.of("pre:A", "invoke:down", "post:A", "after:A"), events);
    }

    @Test
    void duplicateOrderIsRejected() {
        InterceptorManager manager = new InterceptorManager();
        manager.addInterceptionHandler("/a/*", new RecordingInterceptor("A", 1, new ArrayList<>(), true, false));

        assertThrows(IllegalArgumentException.class, () -> manager.addInterceptionHandler(
                "/b/*", new RecordingInterceptor("B", 1, new ArrayList<>(), true, false)));
    }

    @Test
    void nullArgumentsAreRejected() {
        InterceptorManager manager = new InterceptorManager();
        InterceptorHandler handler = new DefaultsOnly();

        assertThrows(NullPointerException.class, () -> manager.addInterceptionHandler(null, handler));
        assertThrows(NullPointerException.class, () -> manager.addInterceptionHandler("/a/*", null));
    }

    @Test
    void defaultInterceptorMethodsPassThrough() {
        DefaultsOnly handler = new DefaultsOnly();

        assertEquals(0, handler.order());
        assertTrue(handler.preHandler(null));
        assertEquals("result", handler.postHandler(null, "result"));
        assertDoesNotThrow(() -> handler.afterCompletion(new RuntimeException("ignored")));
    }
}
