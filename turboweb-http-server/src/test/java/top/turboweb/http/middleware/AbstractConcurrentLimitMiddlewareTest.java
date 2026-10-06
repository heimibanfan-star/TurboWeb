package top.turboweb.http.middleware;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.AbstractConcurrentLimitMiddleware.LimitMatchResult;
import top.turboweb.http.middleware.AbstractConcurrentLimitMiddleware.LimitMatchStatus;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发限流中间件的名额判定（spec §5：middleware/*）。
 *
 * <p>两个限流器内部都只有一把 {@link java.util.concurrent.atomic.AtomicInteger}，判定是纯计数、
 * 无时间窗，因此不需要（也无处）注入 token bucket 的 interval。为了不 sleep、不起真线程就制造
 * "第二个请求在外层名额未释放时进入"，实例版直接调 {@code protected couldEnter/leave}，
 * 全局版用单线程嵌套 {@code invoke}（{@link ReenterOnce}）。
 *
 * <p>本类与产品类同包，因此能访问 {@code protected} 的嵌套类型与 {@code concurrentLimitMap}。
 */
class AbstractConcurrentLimitMiddlewareTest {

    /** 夹具：记录拒绝回调，避免真实响应写出。 */
    private static final class ProbeLimit extends AbstractConcurrentLimitMiddleware {
        final List<String> rejectCalls = new ArrayList<>();

        @Override
        public Object doAfterReject(HttpContext ctx) {
            rejectCalls.add("reject");
            return "REJECTED";
        }
    }

    /** 夹具：全局限流器，记录拒绝回调。 */
    private static final class GlobalLimit extends AbstractGlobalConcurrentLimitMiddleware {
        final List<String> rejectCalls;

        GlobalLimit(int max) {
            this(max, new ArrayList<>());
        }

        /**
         * {@code rejectCalls} 允许由调用方注入并与事件流共用：当需要"拒绝与嵌套返回按真实顺序
         * 落在同一条列表"取证时（见 globalRejectsOverflowAndReleasesSlot），传入调用方自己的列表。
         */
        GlobalLimit(int max, List<String> rejectCalls) {
            super(max);
            this.rejectCalls = rejectCalls;
        }

        @Override
        public Object doAfterReject(HttpContext ctx) {
            rejectCalls.add("reject");
            return "REJECTED";
        }
    }

    /**
     * 夹具：在"外层名额尚未释放"时再调用一次限流器 {@code invoke}，
     * 用单线程嵌套调用确定性地模拟并发的第二个在途请求——不 sleep、不起线程。
     * 首次调用返回 {@code "OUTER"}，嵌套结果写进 events；重复进入时落 {@code "INNER-LEAF"}。
     */
    private static final class ReenterOnce extends Middleware {
        private final AbstractGlobalConcurrentLimitMiddleware limiter;
        private final List<String> events;
        private boolean reentered = false;

        ReenterOnce(AbstractGlobalConcurrentLimitMiddleware limiter, List<String> events) {
            this.limiter = limiter;
            this.events = events;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            if (!reentered) {
                reentered = true;
                Object nested = limiter.invoke(ctx); // 外层名额仍被占着
                events.add("nested=" + nested);
                return "OUTER";
            }
            return "INNER-LEAF";
        }
    }

    /**
     * L1 上下文夹具：内存中的 {@link FullHttpRequest}，只带方法与 URI，不含 channel / socket / 线程。
     *
     * <p>内容缓冲来自 {@code Unpooled.buffer(0)}，非池化、无需 {@code release()}，不触发 Netty 泄漏检测；
     * 其余构造参数（session / cookie / connectSession / 序列化器）在本用例的调用路径上都用不到，传 null。
     */
    private static HttpContext ctx(HttpMethod method, String uri) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        return new FullHttpContext(request, null, null, null, null);
    }

    // ---------- AbstractConcurrentLimitMiddleware（实例版） ----------

    @Test
    @DisplayName("名额判定三态：未注册方法/前缀不匹配为 IGNORE，未超限 ALLOW，超限 REJECT 且回滚计数")
    void couldEnterClassifiesThreeStatuses() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 2);

        assertEquals(LimitMatchStatus.IGNORE, p.couldEnter("POST", "/api/x").status(),
                "未注册的方法完全不参与控制");
        assertEquals(LimitMatchStatus.IGNORE, p.couldEnter("GET", "/other").status(),
                "有该方法的策略但没有前缀匹配");

        LimitMatchResult first = p.couldEnter("GET", "/api/a");
        assertEquals(LimitMatchStatus.ALLOW, first.status());
        assertEquals("/api", first.prefix(), "ALLOW 结果带命中的前缀");

        LimitMatchResult second = p.couldEnter("GET", "/api/b");
        assertEquals(LimitMatchStatus.ALLOW, second.status(), "count==limit 仍放行——判定用 > 而非 >=");

        LimitMatchResult third = p.couldEnter("GET", "/api/c");
        assertEquals(LimitMatchStatus.REJECT, third.status());
        assertEquals("/api", third.prefix(), "REJECT 结果也带命中的前缀");

        // 超限必须把自增回滚：若 :84 的 decrementAndGet 被删，这里 leave 之后
        // 计数会停在 3，下一条请求会被误判为 REJECT
        p.leave("GET", second);
        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api/z").status(),
                "REJECT 路径回滚了计数，leave 一个 ALLOW 后仍能满额放行");
    }

    @Test
    @DisplayName("leave 对 IGNORE/REJECT 结果是 no-op，不会去访问未注册的方法表")
    void leaveSkipsNonAllowResults() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);

        LimitMatchResult ignore = p.couldEnter("POST", "/api/x"); // 方法表里没有 POST 这一项
        assertEquals(LimitMatchStatus.IGNORE, ignore.status());
        // 若 :99 的 ALLOW 守卫被去掉，这里会对 concurrentLimitMap.get("POST")==null 调 get -> NPE
        assertDoesNotThrow(() -> p.leave("POST", ignore));

        LimitMatchResult allow = p.couldEnter("GET", "/api/x");
        assertEquals(LimitMatchStatus.ALLOW, allow.status());
        p.leave("GET", allow);
        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api/y").status(),
                "ALLOW 的 leave 归还名额");
    }

    @Test
    @DisplayName("invoke 拒绝路径：名额耗尽时走 doAfterReject，且不调用后续中间件")
    void rejectPathDoesNotInvokeNext() {
        List<String> events = new ArrayList<>();
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        p.setNext(new RecordingMiddleware("next", events, "OK"));

        // 直接占用唯一名额（不经过 invoke，因此不会触发 finally 里的 leave）
        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api/x").status());

        Object result = p.invoke(ctx(HttpMethod.GET, "/api/x"));

        assertEquals("REJECTED", result);
        assertEquals(List.of("reject"), p.rejectCalls);
        assertTrue(events.isEmpty(), "拒绝路径不得进入后续中间件链: " + events);
    }

    @Test
    @DisplayName("invoke 放行路径：调用后续中间件并在 finally 归还名额")
    void allowPathInvokesNextAndReleasesSlot() {
        List<String> events = new ArrayList<>();
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        p.setNext(new RecordingMiddleware("next", events, "OK"));

        assertEquals("OK", p.invoke(ctx(HttpMethod.GET, "/api/x")));
        // 名额已被 finally 的 leave 归还：第二次（上限 1）仍能放行
        assertEquals("OK", p.invoke(ctx(HttpMethod.GET, "/api/x")));
        assertEquals(List.of("invoke:next", "invoke:next"), events);
        assertTrue(p.rejectCalls.isEmpty(), "名额归还后不应触发拒绝");
    }

    @Test
    @DisplayName("invoke 无策略方法：IGNORE 直接透传，不计数")
    void ignorePathPassesThrough() {
        List<String> events = new ArrayList<>();
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        p.setNext(new RecordingMiddleware("next", events, "OK"));

        assertEquals("OK", p.invoke(ctx(HttpMethod.POST, "/api/x")));
        assertEquals("OK", p.invoke(ctx(HttpMethod.POST, "/api/x")));
        assertEquals(List.of("invoke:next", "invoke:next"), events);
        assertTrue(p.rejectCalls.isEmpty());
    }

    @Test
    @DisplayName("不同请求方法的名额互相独立")
    void countersAreSeparatedByMethod() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        p.addStrategy(HttpMethod.POST, "/api", 1);

        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api").status());
        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("POST", "/api").status(), "POST 用的是自己的名额");
        assertEquals(LimitMatchStatus.REJECT, p.couldEnter("GET", "/api").status());
        assertEquals(LimitMatchStatus.REJECT, p.couldEnter("POST", "/api").status());
    }

    @Test
    @DisplayName("钉住 HS-3：重复 addStrategy 静默重置已有计数")
    void duplicateAddStrategyResetsCounter() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 2);
        p.couldEnter("GET", "/api/a");
        p.couldEnter("GET", "/api/b"); // 名额已用满

        // 只发一条 warn 日志（本套用例不靠日志取证），随后无条件覆盖为新 ConcurrentLimit
        p.addStrategy(HttpMethod.GET, "/api", 2);

        // 钉住 HS-3：若将来修正（改为保留已有计数或拒绝重复登记），本用例应改为期望正确行为
        assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api/c").status(),
                "重复登记把计数从 2 归零，于是又放行了第 3 个请求");
    }

    @Test
    @DisplayName("钉住 HS-4：前缀重叠时按 map 顺序取首个匹配，没有最具体优先")
    void overlappingPrefixesConsultOnlyFirstMatch() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        p.addStrategy(HttpMethod.GET, "/api/users", 1);

        LimitMatchResult hit = p.couldEnter("GET", "/api/users/1");
        assertEquals(LimitMatchStatus.ALLOW, hit.status());
        // 钉住 HS-4：若将来修正（最具体前缀优先），本用例应改为期望正确行为；
        // 现状是两个前缀都匹配，命中哪个取决于 HashMap 迭代顺序（不固定）
        assertTrue(hit.prefix().equals("/api") || hit.prefix().equals("/api/users"), hit.prefix());
        if (hit.prefix().equals("/api/users")) {
            assertEquals(LimitMatchStatus.ALLOW, p.couldEnter("GET", "/api/x").status(),
                    "只消费了 /api/users，/api 的名额应原封不动");
        } else {
            assertEquals(LimitMatchStatus.REJECT, p.couldEnter("GET", "/api/x").status(),
                    "/api 的名额已被消费");
        }
    }

    @Test
    @DisplayName("leave 对已消失的策略是 no-op（防御分支）")
    void leaveOnMissingStrategyIsNoOp() {
        ProbeLimit p = new ProbeLimit();
        p.addStrategy(HttpMethod.GET, "/api", 1);
        LimitMatchResult allow = p.couldEnter("GET", "/api/x");
        assertEquals(LimitMatchStatus.ALLOW, allow.status());

        // 无 remove API，直接清空方法表模拟策略在请求在途期间消失；若 :104 的空判被删则 NPE
        p.concurrentLimitMap.get("GET").clear();

        assertDoesNotThrow(() -> p.leave("GET", allow));
    }

    // ---------- AbstractGlobalConcurrentLimitMiddleware（全局版） ----------

    @Test
    @DisplayName("全局限流：达到上限即拒绝，外层结束后 finally 释放名额")
    void globalRejectsOverflowAndReleasesSlot() {
        List<String> events = new ArrayList<>();
        // 拒绝回调与嵌套事件共用同一条列表，按真实时间顺序取证：["reject", "nested=REJECTED"]
        GlobalLimit g = new GlobalLimit(1, events);
        g.setNext(new ReenterOnce(g, events));

        // 外层占住唯一名额，嵌套的第二次进入必然超限走 doAfterReject
        assertEquals("OUTER", g.invoke(ctx(HttpMethod.GET, "/x")));
        assertEquals(List.of("reject", "nested=REJECTED"), events);

        // 外层 finally 已 leave：第二次进入不再超限（ReenterOnce 已置位，直接落叶子）
        assertEquals("INNER-LEAF", g.invoke(ctx(HttpMethod.GET, "/x")),
                "外层结束后必须归还名额，否则这次会再次 REJECT");
        assertEquals(List.of("reject", "nested=REJECTED"), events, "第二次进入不得再触发拒绝");
    }

    @Test
    @DisplayName("全局限流：拒绝路径直接返回 doAfterReject，不进入后续链")
    void globalRejectSkipsNext() {
        List<String> events = new ArrayList<>();
        GlobalLimit g = new GlobalLimit(0); // 上限 0：任何进入都超限
        g.setNext(new RecordingMiddleware("next", events, "OK"));

        assertEquals("REJECTED", g.invoke(ctx(HttpMethod.GET, "/x")));
        assertEquals(List.of("reject"), g.rejectCalls);
        assertTrue(events.isEmpty(), "上限 0 时不得调用后续中间件");
    }

    @Test
    @DisplayName("全局限流：链尾无 next 时放行返回 null，且每次都已归还名额")
    void globalAllowWithoutNextReturnsNull() {
        GlobalLimit g = new GlobalLimit(2);

        assertNull(g.invoke(ctx(HttpMethod.GET, "/x")));
        assertNull(g.invoke(ctx(HttpMethod.GET, "/x")));
        // 第三次仍放行 -> 前两次 finally 的 leave 生效、计数没有累积；若 leave 缺失这次会 REJECT
        assertNull(g.invoke(ctx(HttpMethod.GET, "/x")));
        assertTrue(g.rejectCalls.isEmpty());
    }
}
