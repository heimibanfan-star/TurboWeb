package top.turboweb.http.middleware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * middleware 顶层包的类型分派家族（spec §5：{@code CoreTypeMiddleware}/{@code MixedMiddleware}/
 * {@code TypedSkipMiddleware}/{@code BranchMiddleware} 类型分派）与 {@link BranchMiddleware} 的
 * 分支组装/合并语义。
 *
 * <p>所有用例的 {@code ctx} 均为 {@code null}：这些类只把 ctx 透传给后继或交由测试子类实现的钩子，
 * 不读写它（见任务章"ctx 取值说明"）。
 */
class MiddlewareTypesTest {

    // ---------------- 类型分派家族夹具 ----------------

    /** 直接暴露 {@link CoreTypeMiddleware} 两个默认钩子：不覆写 preNext/onBreak。 */
    private static class CoreProbe extends CoreTypeMiddleware {
        @Override
        public Object invoke(HttpContext ctx) {
            if (!preNext(ctx)) {
                return onBreak(ctx);
            }
            return next(ctx);
        }
    }

    /** preNext 拒绝，用来观测 {@link CoreTypeMiddleware} 的默认 onBreak。 */
    private static class BlockingCoreProbe extends CoreTypeMiddleware {
        @Override
        protected boolean preNext(HttpContext ctx) {
            return false;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            if (!preNext(ctx)) {
                return onBreak(ctx);
            }
            return next(ctx);
        }
    }

    /** 固定返回值的中间件，给上层分派中间件喂不同类型的返回值。 */
    private static class ConstantMiddleware extends Middleware {
        private final Object value;

        ConstantMiddleware(Object value) {
            this.value = value;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            return value;
        }
    }

    /** Publisher 的最小实现：本用例只关心 instanceof Publisher 这一条分支，不真正订阅。 */
    private static final class StubPublisher implements Publisher<Object> {
        private final String tag;

        StubPublisher(String tag) {
            this.tag = tag;
        }

        @Override
        public void subscribe(Subscriber<? super Object> subscriber) {
            // 不使用
        }

        @Override
        public String toString() {
            return "StubPublisher(" + tag + ")";
        }
    }

    /** MixedMiddleware 探针：同步/异步两条分支各自记名并改写返回值。 */
    private static class MixedProbe extends MixedMiddleware {
        private final List<String> log;
        final Publisher<?> asyncResult = new StubPublisher("out");

        MixedProbe(List<String> log) {
            this.log = log;
        }

        @Override
        protected Object afterSyncNext(HttpContext ctx, Object result) {
            log.add("sync:" + result);
            return "SYNC(" + result + ")";
        }

        @Override
        protected Publisher<?> afterAsyncNext(HttpContext ctx, Publisher<?> publisher) {
            log.add("async:" + publisher);
            return asyncResult;
        }
    }

    /** preNext 拒绝的 MixedMiddleware：两条 after* 都不该被触碰。 */
    private static class BlockingMixedProbe extends MixedMiddleware {
        @Override
        protected boolean preNext(HttpContext ctx) {
            return false;
        }

        @Override
        protected Object onBreak(HttpContext ctx) {
            return "BREAK";
        }

        @Override
        protected Object afterSyncNext(HttpContext ctx, Object result) {
            throw new AssertionError("preNext 中断后不得进入 afterSyncNext");
        }

        @Override
        protected Publisher<?> afterAsyncNext(HttpContext ctx, Publisher<?> publisher) {
            throw new AssertionError("preNext 中断后不得进入 afterAsyncNext");
        }
    }

    /** 只处理 String 的 TypedMiddleware。 */
    private static class StringTyped extends TypedMiddleware<String> {
        private final List<String> log;

        StringTyped(List<String> log) {
            this.log = log;
        }

        @Override
        protected String afterNext(HttpContext ctx, String result) {
            log.add("after:" + result);
            return result.toUpperCase();
        }
    }

    /** T=Object：TypeParameterMatcher 退化为永真匹配，用来观测 null 短路的存在性。 */
    private static class ObjectTyped extends TypedMiddleware<Object> {
        private int calls;

        @Override
        protected Object afterNext(HttpContext ctx, Object result) {
            calls++;
            return result;
        }
    }

    private static class BlockingStringTyped extends TypedMiddleware<String> {
        @Override
        protected boolean preNext(HttpContext ctx) {
            return false;
        }

        @Override
        protected Object onBreak(HttpContext ctx) {
            return "BREAK";
        }

        @Override
        protected String afterNext(HttpContext ctx, String result) {
            throw new AssertionError("preNext 中断后不得进入 afterNext");
        }
    }

    /** 跳过 String 的 TypedSkipMiddleware。 */
    private static class StringSkipper extends TypedSkipMiddleware<String> {
        private final List<String> log;

        StringSkipper(List<String> log) {
            this.log = log;
        }

        @Override
        protected Object afterNext(HttpContext ctx, Object result) {
            log.add("after:" + result);
            return "HANDLED(" + result + ")";
        }
    }

    private static class BlockingStringSkipper extends TypedSkipMiddleware<String> {
        @Override
        protected boolean preNext(HttpContext ctx) {
            return false;
        }

        @Override
        protected Object onBreak(HttpContext ctx) {
            return "BREAK";
        }

        @Override
        protected Object afterNext(HttpContext ctx, Object result) {
            throw new AssertionError("preNext 中断后不得进入 afterNext");
        }
    }

    // ---------------- BranchMiddleware 夹具 ----------------

    /** 固定分支键的分支中间件；用例都不读 ctx。 */
    private static class FixedBranch extends BranchMiddleware {
        private final String key;

        FixedBranch(String key) {
            this.key = key;
        }

        @Override
        protected String getBranchKey(HttpContext ctx) {
            return key;
        }
    }

    /** 记录 init 收到的 chain 参数、invoke/destroy 顺序的分支成员。 */
    private static class ChainProbe extends Middleware {
        private final String name;
        private final List<String> events;
        private final String payload;
        Middleware initChainArg;
        int initCalls;

        ChainProbe(String name, List<String> events, String payload) {
            this.name = name;
            this.events = events;
            this.payload = payload;
        }

        @Override
        public void init(Middleware chain) {
            initCalls++;
            initChainArg = chain;
            events.add("init:" + name);
        }

        @Override
        public Object invoke(HttpContext ctx) {
            events.add("invoke:" + name);
            return payload != null ? payload : next(ctx);
        }

        @Override
        public void destroy() {
            events.add("destroy:" + name);
        }
    }

    // ---------------- CoreTypeMiddleware：默认钩子 ----------------

    @Test
    @DisplayName("CoreTypeMiddleware 默认 preNext 返回 true，链继续向后")
    void coreTypeDefaultPreNextAllowsChainToContinue() {
        List<String> events = new ArrayList<>();
        CoreProbe head = new CoreProbe();
        head.setNext(new RecordingMiddleware("tail", events, "body"));

        assertEquals("body", head.invoke(null));
        assertEquals(List.of("invoke:tail"), events);
    }

    @Test
    @DisplayName("CoreTypeMiddleware 默认 onBreak 返回 null，且中断时不触碰后继")
    void coreTypeDefaultOnBreakReturnsNull() {
        List<String> events = new ArrayList<>();
        BlockingCoreProbe head = new BlockingCoreProbe();
        head.setNext(new RecordingMiddleware("tail", events, "body"));

        assertNull(head.invoke(null), "默认 onBreak 返回 null");
        assertTrue(events.isEmpty(), "preNext 拒绝时不得触碰链上的后继");
    }

    // ---------------- MixedMiddleware：同步 / 异步分派 ----------------

    @Test
    @DisplayName("MixedMiddleware：同步返回值走 afterSyncNext")
    void mixedRoutesSyncResultToAfterSyncNext() {
        List<String> log = new ArrayList<>();
        List<String> events = new ArrayList<>();
        MixedProbe head = new MixedProbe(log);
        head.setNext(new RecordingMiddleware("tail", events, "body"));

        assertEquals("SYNC(body)", head.invoke(null));
        assertEquals(List.of("sync:body"), log);
    }

    @Test
    @DisplayName("MixedMiddleware：Publisher 返回值走 afterAsyncNext")
    void mixedRoutesPublisherResultToAfterAsyncNext() {
        List<String> log = new ArrayList<>();
        MixedProbe head = new MixedProbe(log);
        head.setNext(new ConstantMiddleware(new StubPublisher("up")));

        assertSame(head.asyncResult, head.invoke(null), "Publisher 分支的返回值由 afterAsyncNext 决定");
        assertEquals(List.of("async:StubPublisher(up)"), log);
    }

    @Test
    @DisplayName("MixedMiddleware：preNext 拒绝时走 onBreak，两条 after* 都不执行")
    void mixedPreNextFalseBreaksWithoutCallingAfterHooks() {
        BlockingMixedProbe head = new BlockingMixedProbe();

        assertEquals("BREAK", head.invoke(null), "after* 被覆写成抛 AssertionError，走到即红");
    }

    // ---------------- TypedMiddleware：命中 T 才处理 ----------------

    @Test
    @DisplayName("TypedMiddleware：返回值属于 T 时进入 afterNext")
    void typedProcessesMatchingType() {
        List<String> log = new ArrayList<>();
        StringTyped head = new StringTyped(log);
        head.setNext(new ConstantMiddleware("abc"));

        assertEquals("ABC", head.invoke(null));
        assertEquals(List.of("after:abc"), log);
    }

    @Test
    @DisplayName("TypedMiddleware：返回值不属于 T 时原样透传，afterNext 不执行")
    void typedPassesThroughNonMatchingType() {
        List<String> log = new ArrayList<>();
        Integer seven = 7;
        StringTyped head = new StringTyped(log);
        head.setNext(new ConstantMiddleware(seven));

        assertSame(seven, head.invoke(null));
        assertTrue(log.isEmpty(), "非 T 类型不得进入 afterNext");
    }

    @Test
    @DisplayName("TypedMiddleware：null 结果在类型匹配前短路（T=Object 时可见）")
    void typedGuardsNullBeforeTypeMatch() {
        ObjectTyped head = new ObjectTyped();
        head.setNext(new ConstantMiddleware(null));

        assertNull(head.invoke(null));
        assertEquals(0, head.calls,
                "T=Object 时 matcher 永真，null 必须被前置的 null 短路挡住，不能进 afterNext");
    }

    @Test
    @DisplayName("TypedMiddleware：preNext 拒绝时走 onBreak，afterNext 不执行")
    void typedPreNextFalseBreaksChain() {
        BlockingStringTyped head = new BlockingStringTyped();

        assertEquals("BREAK", head.invoke(null), "afterNext 被覆写成抛 AssertionError，走到即红");
    }

    // ---------------- TypedSkipMiddleware：命中 T 就跳过 ----------------

    @Test
    @DisplayName("TypedSkipMiddleware：返回值属于 T 时跳过，afterNext 不执行")
    void typedSkipSkipsMatchingType() {
        List<String> log = new ArrayList<>();
        StringSkipper head = new StringSkipper(log);
        head.setNext(new ConstantMiddleware("abc"));

        assertEquals("abc", head.invoke(null), "T 类型被跳过，返回值不变");
        assertTrue(log.isEmpty());
    }

    @Test
    @DisplayName("TypedSkipMiddleware：返回值不属于 T 时进入 afterNext")
    void typedSkipProcessesNonMatchingType() {
        List<String> log = new ArrayList<>();
        StringSkipper head = new StringSkipper(log);
        head.setNext(new ConstantMiddleware(7));

        assertEquals("HANDLED(7)", head.invoke(null));
        assertEquals(List.of("after:7"), log);
    }

    @Test
    @DisplayName("TypedSkipMiddleware 没有 null 短路，null 会进入 afterNext（与 TypedMiddleware:46-48 不对称）")
    void typedSkipRoutesNullThroughAfterNext() {
        List<String> log = new ArrayList<>();
        StringSkipper head = new StringSkipper(log);
        head.setNext(new ConstantMiddleware(null));

        assertEquals("HANDLED(null)", head.invoke(null),
                "String 的 matcher 对 null 判 false，于是落到 afterNext——行为记录，不是 spec §8 候选");
        assertEquals(List.of("after:null"), log);
    }

    @Test
    @DisplayName("TypedSkipMiddleware：preNext 拒绝时走 onBreak，afterNext 不执行")
    void typedSkipPreNextFalseBreaksChain() {
        BlockingStringSkipper head = new BlockingStringSkipper();

        assertEquals("BREAK", head.invoke(null), "afterNext 被覆写成抛 AssertionError，走到即红");
    }

    // ---------------- BranchMiddleware：注册与校验 ----------------

    @Test
    @DisplayName("addMiddleware 拒绝 null 分支键与 null 中间件")
    void addMiddlewareRejectsNullKeyAndMiddleware() {
        FixedBranch branch = new FixedBranch("a");

        assertThrows(NullPointerException.class,
                () -> branch.addMiddleware(null, new ChainProbe("m", new ArrayList<>(), null)));
        assertThrows(NullPointerException.class,
                () -> branch.addMiddleware("a", null));
    }

    @Test
    @DisplayName("addMiddleware 返回 this，支持链式注册")
    void addMiddlewareReturnsThis() {
        FixedBranch branch = new FixedBranch("a");

        assertSame(branch, branch.addMiddleware("a", new ChainProbe("m", new ArrayList<>(), null)));
    }

    @Test
    @DisplayName("同一分支重复添加同一中间件实例抛 TurboServerInitException")
    void duplicateMiddlewareInSameBranchIsRejected() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m = new ChainProbe("m", events, null);
        branch.addMiddleware("a", m);

        // LinkedHashSet 判重走 Middleware 未覆写的 equals → 只对同一实例生效
        TurboServerInitException ex = assertThrows(TurboServerInitException.class,
                () -> branch.addMiddleware("a", m));
        assertTrue(ex.getMessage().contains("Duplicate middleware"), ex.getMessage());
    }

    // ---------------- BranchMiddleware：分派与合并 ----------------

    @Test
    @DisplayName("invoke 按注册顺序执行命中的分支链")
    void invokeDelegatesToBranchChainInRegistrationOrder() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        branch.addMiddleware("a", new ChainProbe("m1", events, null));
        branch.addMiddleware("a", new ChainProbe("m2", events, "A"));
        branch.init(branch);

        assertEquals("A", branch.invoke(null), "m1 无载荷继续向后，m2 携带载荷终止分支链");
        assertEquals(List.of("init:m1", "init:m2", "invoke:m1", "invoke:m2"), events);
    }

    @Test
    @DisplayName("分支链走完后经 MergeMiddleware 回到主链后继")
    void branchChainMergesBackIntoMainChain() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m1 = new ChainProbe("m1", events, null);
        RecordingMiddleware mainTail = new RecordingMiddleware("main", events, "MAIN");
        branch.setNext(mainTail);
        branch.addMiddleware("a", m1);
        branch.init(branch);

        assertEquals("MAIN", branch.invoke(null), "MergeMiddleware 继续执行主链的下一个中间件");
        assertEquals(List.of("init:m1", "invoke:m1", "invoke:main"), events);
    }

    @Test
    @DisplayName("MergeMiddleware 接的是 getNext()，init 传入的 chain 参数被忽略")
    void initIgnoresChainArgumentAndMergesToGetNext() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m1 = new ChainProbe("m1", events, null);
        RecordingMiddleware mainTail = new RecordingMiddleware("main", events, "MAIN");
        RecordingMiddleware decoy = new RecordingMiddleware("decoy", events, "DECOY");
        branch.setNext(mainTail);
        branch.addMiddleware("a", m1);

        branch.init(decoy);

        assertEquals("MAIN", branch.invoke(null), "chain 参数与 getNext() 不同也不影响合并目标");
        assertFalse(events.contains("invoke:decoy"), "被传入的 chain 参数不得被调用");
    }

    @Test
    @DisplayName("分支内每个中间件的 init 收到的 chain 参数是分支链头")
    void initPassesBranchHeadAsChainArgumentToBranchMembers() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m1 = new ChainProbe("m1", events, null);
        ChainProbe m2 = new ChainProbe("m2", events, "A");
        branch.addMiddleware("a", m1);
        branch.addMiddleware("a", m2);

        branch.init(branch);

        assertEquals(1, m1.initCalls);
        assertEquals(1, m2.initCalls);
        assertSame(m1, m1.initChainArg, "分支首件收到自己（分支链头）作为 chain");
        assertSame(m1, m2.initChainArg, "分支后续件同样收到分支链头，而不是 init 传入的主链参数");
    }

    @Test
    @DisplayName("init 后分支成员被锁定，链不可重接")
    void initLocksBranchMembersAgainstRewiring() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m1 = new ChainProbe("m1", events, null);
        branch.addMiddleware("a", m1);
        branch.addMiddleware("a", new ChainProbe("m2", events, "A"));
        branch.init(branch);

        m1.setNext(new ChainProbe("intruder", events, "X"));

        assertEquals("A", branch.invoke(null), "锁定后 setNext 只告警，链维持原样");
        assertFalse(events.contains("invoke:intruder"));
    }

    @Test
    @DisplayName("invoke 只执行 getBranchKey 命中的那条分支链")
    void invokeSelectsBranchByKey() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("b");
        branch.addMiddleware("a", new ChainProbe("a1", events, "A"));
        branch.addMiddleware("b", new ChainProbe("b1", events, "B"));
        branch.init(branch);

        assertEquals("B", branch.invoke(null));
        assertFalse(events.contains("invoke:a1"), "未命中的分支链不得执行");
    }

    @Test
    @DisplayName("invoke 遇到未注册的分支键抛 TurboRouterException(ROUTER_NOT_MATCH)")
    void invokeUnknownBranchThrowsRouterNotMatch() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("missing");
        branch.addMiddleware("a", new ChainProbe("a1", events, "A"));
        branch.init(branch);

        TurboRouterException ex =
                assertThrows(TurboRouterException.class, () -> branch.invoke(null));
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, ex.getCode());
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
    }

    @Test
    @DisplayName("未 init 时注册过分支也视为不存在：invoke 抛 ROUTER_NOT_MATCH")
    void invokeBeforeInitTreatsBranchAsMissing() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        branch.addMiddleware("a", new ChainProbe("a1", events, "A"));

        // middlewareChains 只在 init 里写入，注册本身不等于可路由
        TurboRouterException ex =
                assertThrows(TurboRouterException.class, () -> branch.invoke(null));
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    @DisplayName("F1 回归：BranchMiddleware 覆写 destroy 后分支成员收到销毁回调")
    void branchMembersReceiveDestroyFromBranchMiddleware() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        branch.addMiddleware("a", new ChainProbe("m1", events, null));
        branch.init(branch);

        branch.destroy();

        assertEquals(List.of("init:m1", "destroy:m1"), events,
                "分支成员按注册序经 BranchMiddleware.destroy 覆写各收到一次销毁回调");
    }

    @Test
    @DisplayName("观察：init 的锁定循环越过 MergeMiddleware，把主链后继也锁上")
    void initLockTraversalContinuesIntoMainChain() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        ChainProbe m1 = new ChainProbe("m1", events, null);
        RecordingMiddleware mainTail = new RecordingMiddleware("main", events, "MAIN");
        branch.setNext(mainTail);
        branch.addMiddleware("a", m1);

        branch.init(branch);
        mainTail.setNext(new RecordingMiddleware("intruder", events, "X"));

        assertNull(mainTail.getNext(),
                "锁定循环 while (cursor != null) 只跳过 MergeMiddleware 本身，随后沿 merge.getNext() "
                        + "继续锁定主链后继——行为记录，非 spec §8 候选，未裁决");
        assertFalse(events.contains("invoke:intruder"));
    }
}
