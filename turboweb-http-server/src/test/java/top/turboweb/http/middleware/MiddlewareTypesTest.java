package top.turboweb.http.middleware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BranchMiddleware} 的分支注册、分派、合并与销毁语义。
 *
 * <p>所有用例的 {@code ctx} 均为 {@code null}：这些类只把 ctx 透传给后继或交由测试子类实现的钩子，
 * 不读写它（见任务章"ctx 取值说明"）。
 */
class MiddlewareTypesTest {

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
    @DisplayName("invoke 遇到未注册的分支键抛 RouteHandlerException(ROUTER_NOT_MATCH)")
    void invokeUnknownBranchThrowsRouterNotMatch() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("missing");
        branch.addMiddleware("a", new ChainProbe("a1", events, "A"));
        branch.init(branch);

        RouteHandlerException ex =
                assertThrows(RouteHandlerException.class, () -> branch.invoke(null));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
    }

    @Test
    @DisplayName("未 init 时注册过分支也视为不存在：invoke 抛 ROUTER_NOT_MATCH")
    void invokeBeforeInitTreatsBranchAsMissing() {
        List<String> events = new ArrayList<>();
        FixedBranch branch = new FixedBranch("a");
        branch.addMiddleware("a", new ChainProbe("a1", events, "A"));

        // middlewareChains 只在 init 里写入，注册本身不等于可路由
        RouteHandlerException ex =
                assertThrows(RouteHandlerException.class, () -> branch.invoke(null));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
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
