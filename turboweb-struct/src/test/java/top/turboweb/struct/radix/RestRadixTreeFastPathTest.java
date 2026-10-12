package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestRadixTree} 查询优化开关（构造器 boolean 参数）：开启时纯字面量模式
 * 进入哈希快表，匹配先查快表（键为归一化后的段序列）未果再走树；未开启时全部
 * 模式按原逻辑进树。两种模式对外的注册、冲突、匹配与删除语义完全一致，
 * 字面量对参数的字面量优先口径同样经由快表命中保持。
 */
class RestRadixTreeFastPathTest {

    @Test
    void literalPatternMatchesThroughFastPath() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/a");
        assertEquals(1, hit.value());
        assertEquals(Map.of(), hit.params(), "纯字面量命中无参数绑定");
    }

    @Test
    void literalFastPathGetAndExistReadThroughMap() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a/b", 1);
        tree.put("/:x/b", 2);
        assertEquals(1, tree.get("//a//b/").value(), "快表键与匹配同口径归一化，get 直接取回");
        assertEquals(Map.of(), tree.get("/a/b").params(), "快表命中无参数绑定");
        assertTrue(tree.exist("/a/b"));
        assertFalse(tree.exist("/a/c"), "快表未果且树侧无该字面链即未命中");
        assertEquals(2, tree.get("/:x/b").value(), "含参模式按注册原文走树精确取回");
        assertTrue(tree.exist("/:x/b"));
        assertNull(tree.get("/q/b"), "参数分支命中的路径不经 get 通配");
        assertFalse(tree.exist("/q/b"), "参数分支命中的路径 exist 同按未命中");
        assertEquals(2, tree.matchAny("/q/b").value(), "同一路径 matchAny 经参数分支命中");
    }

    @Test
    void literalFastPathDuplicateRegistrationRejected() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a", 1);
        assertEquals("Path pattern '/a' is already registered",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a", 2)).getMessage());
        assertEquals(1, tree.matchAny("/a").value(), "拒收后原注册不受影响");
    }

    @Test
    void literalFastPathKeyNormalizesLikeTreeMatch() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a/b", 1);
        assertEquals(1, tree.matchAny("//a//b/").value(), "快表键与树匹配同口径归一化");
    }

    @Test
    void literalFastPathLiteralWinsOverParamSibling() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/:x", 2);
        tree.put("/a", 1);
        assertEquals(1, tree.matchAny("/a").value(), "字面量命中优先于参数分支");
        RouteTree.Result<Integer> miss = tree.matchAny("/b");
        assertEquals(2, miss.value(), "快表未命中回落树匹配");
        assertEquals(Map.of("x", "b"), miss.params());
    }

    @Test
    void literalFastPathLiteralWinsOverParamAtDeeperAlignment() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a/:x", 2);
        tree.put("/a/b", 1);
        assertEquals(1, tree.matchAny("/a/b").value(), "深层对齐时字面量同样优先");
        RouteTree.Result<Integer> miss = tree.matchAny("/a/c");
        assertEquals(2, miss.value());
        assertEquals(Map.of("x", "c"), miss.params());
    }

    @Test
    void literalFastPathKeepsParamConflictDetection() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/:x/b", 1);
        assertEquals("Ambiguous path pattern '/:y/b' conflicts with existing pattern '/:x/b'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:y/b", 2)).getMessage(),
                "含参模式仍走树的冲突检测");
    }

    @Test
    void literalFastPathRemoveDeletesLiteralAndAllowsReregister() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a", 1);
        assertTrue(tree.remove("/a"));
        assertNull(tree.matchAny("/a"), "删除后快表与树均无残留");
        tree.put("/a", 3);
        assertEquals(3, tree.matchAny("/a").value(), "删除后可重新注册");
    }

    @Test
    void literalFastPathRemoveUnknownReturnsFalse() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        assertFalse(tree.remove("/missing"));
        assertFalse(tree.remove("/:x/b"), "未注册的含参模式同样返回 false");
    }

    @Test
    void literalFastPathMixedPatternStillRemovedViaTree() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/:x/b", 1);
        assertTrue(tree.remove("/:x/b"));
        assertNull(tree.matchAny("/a/b"), "含参模式删除后树侧摘除");
        tree.put("/:y/b", 2);
        assertEquals(2, tree.matchAny("/a/b").value(), "删除后同槽异名参数可重新注册");
    }

    @Test
    void literalFastPathColonLedPathBindsAsParamNotLiteralKey() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a", 1);
        tree.put("/:x", 2);
        assertNull(tree.matchAny("/:literal/nowhere"),
                "冒号前缀段不构成快表键，树内无同段数模式即未命中");
        RouteTree.Result<Integer> hit = tree.matchAny("/:x");
        assertEquals(2, hit.value(), "冒号前缀路径段作为参数值绑定");
        assertEquals(Map.of("x", ":x"), hit.params());
    }

    @Test
    void literalFastPathValueNullStillRejectedBeforeRouting() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a", null)).getMessage());
        assertNull(tree.matchAny("/a"), "拒收后未注册");
    }

    @Test
    void literalFastPathInteractsWithSeal() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(true);
        tree.put("/a", 1);
        RouteTree<Integer> view = tree.toReadView();
        assertThrows(IllegalStateException.class, () -> tree.put("/b", 2),
                "封存对快路径写侧同样生效");
        assertEquals(1, tree.matchAny("/a").value());
        assertEquals(1, view.match("/a").value(), "封存前注册的快表内容对视图可见");
    }

    @Test
    void disabledFastPathKeepsTreeLogicForAllPatterns() {
        RestRadixTree<Integer> tree = new RestRadixTree<>(false);
        tree.put("/a", 1);
        tree.put("/:x", 2);
        assertEquals(1, tree.matchAny("/a").value());
        assertEquals(2, tree.matchAny("/b").value(), "未开启时全部模式走树");
        assertEquals("Path pattern '/a' is already registered",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a", 3)).getMessage());
    }
}
