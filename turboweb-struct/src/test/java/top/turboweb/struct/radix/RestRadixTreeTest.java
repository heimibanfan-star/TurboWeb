package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestRadixTree} 的 put 分类、put / remove 校验全表、remove miss 基线、
 * matchAny 回溯匹配与不支持读方法。
 * <p>结构断言经反射读取节点字段，dump 形如 [seg=value [子节点]]，
 * 字面子节点按键排序、参数子节点殿后且段名记 ":name"；根节点段为空、值恒为 null。
 */
class RestRadixTreeTest {

    // ---------- put ----------

    @Test
    void putOnEmptyTreeCreatesLiteralChain() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/a/b", 1));
        assertEquals("[=null [a=null [b=1]]]", dump(tree));
    }

    @Test
    void putSamePatternOverwritesAndReturnsOldValue() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/a/b", 1));
        assertEquals(1, tree.put("/a/b", 2));
        assertEquals("[=null [a=null [b=2]]]", dump(tree));
    }

    @Test
    void putParamPatternOverwriteReusesNodeAndReturnsOldValue() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/x/:id", 1));
        assertEquals(1, tree.put("/x/:id", 2));
        assertEquals("[=null [x=null [:id=2]]]", dump(tree));
    }

    @Test
    void putLiteralAndParamCoexistInSameSlotWithLiteralFirst() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        assertNull(tree.put("/:x", 2));
        assertEquals("[=null [a=1] [:x=2]]", dump(tree));
    }

    @Test
    void putLiteralAndParamChainsContinueBelowParamNode() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/:x/a", 1));
        assertNull(tree.put("/:x/:y", 2));
        assertEquals("[=null [:x=null [a=1] [:y=2]]]", dump(tree));
    }

    @Test
    void putSameNameParamsInDifferentPatternsShareParamNode() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/:x/aaa/bbb", 1));
        assertNull(tree.put("/:x/ccc/ddd", 2));
        assertEquals("[=null [:x=null [aaa=null [bbb=1]] [ccc=null [ddd=2]]]]", dump(tree));
    }

    @Test
    void putLiteralsSortedAndParamChildLast() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/b", 1);
        tree.put("/a", 2);
        assertNull(tree.put("/:x", 3));
        assertEquals("[=null [a=2] [b=1] [:x=3]]", dump(tree));
    }

    // ---------- put 校验全表 ----------

    @Test
    void putRejectsNullKey() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put(null, 1)).getMessage());
    }

    @Test
    void putChecksValueBeforeLeadingSlash() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("abc", null)).getMessage());
    }

    @Test
    void putRejectsNullValue() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a", null)).getMessage());
    }

    @Test
    void putRejectsPatternWithoutLeadingSlash() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("abc", 1)).getMessage());
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("", 1)).getMessage());
    }

    @Test
    void putRejectsEmptySegments() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a//b", 1)).getMessage());
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/", 1)).getMessage());
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/", 1)).getMessage());
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.put("//a", 1)).getMessage());
    }

    @Test
    void putRejectsInvalidParamSegment() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Invalid path variable: :1abc",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:1abc", 1)).getMessage());
        assertEquals("Invalid path variable: :a-b",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:a-b", 1)).getMessage());
        assertEquals("Invalid path variable: :",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:", 1)).getMessage());
        assertEquals("Invalid path variable: :a b",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:a b", 1)).getMessage());
    }

    @Test
    void putAcceptsValidParamNameBoundaries() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/:a", 1));
        assertNull(tree.put("/b/:_x1", 2));
        assertNull(tree.put("/c/:A9_", 3));
        assertEquals("[=null [b=null [:_x1=2]] [c=null [:A9_=3]] [:a=1]]", dump(tree));
    }

    @Test
    void putRejectsDuplicateParamNameInPattern() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Duplicate path variable 'x' in path",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:x/b/:x", 1)).getMessage());
    }

    @Test
    void putRejectsConflictingParamNameInSlotAndKeepsTreeUnchanged() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.put("/path/:param1", 1));
        assertEquals("Conflicting path variable ':param2' with existing ':param1'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/path/:param2", 2)).getMessage());
        assertEquals("[=null [path=null [:param1=1]]]", dump(tree));
    }

    // ---------- remove 校验与 miss 基线 ----------

    @Test
    void removeRejectsNullKey() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.remove(null)).getMessage());
    }

    @Test
    void removeRejectsPatternWithoutLeadingSlash() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("abc")).getMessage());
    }

    @Test
    void removeRejectsEmptySegments() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a//b")).getMessage());
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a/")).getMessage());
    }

    @Test
    void removeRejectsInvalidParamSegment() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Invalid path variable: :1abc",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/:1abc")).getMessage());
    }

    @Test
    void removeRejectsDuplicateParamNameInPattern() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("Duplicate path variable 'x' in path",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a/:x/b/:x")).getMessage());
    }

    @Test
    void removeMissOnEmptyTreeReturnsFalse() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertFalse(tree.remove("/a"), "字面分支不存在");
        assertFalse(tree.remove("/:x"), "参数分支不存在");
    }

    @Test
    void removeMissOnAbsentBranchReturnsFalse() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        tree.put("/:x/b", 2);
        assertFalse(tree.remove("/b"), "字面分支不存在");
        assertFalse(tree.remove("/a/c"), "深层字面分支不存在");
        assertFalse(tree.remove("/:x"), "参数节点无值");
        assertFalse(tree.remove("/:x/c"), "参数链下字面分支不存在");
    }

    // ---------- matchAny ----------

    @Test
    void matchAnyRejectsNullPath() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The path argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.matchAny(null)).getMessage());
    }

    @Test
    void matchAnyOnEmptyTreeReturnsNull() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertNull(tree.matchAny("/a"));
    }

    @Test
    void matchAnyHitsLiteralPatternWithoutParams() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/a/b");
        assertEquals(1, hit.value);
        assertEquals(Map.of(), hit.params);
    }

    @Test
    void matchAnyBindsSingleParam() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/:id", 1);
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/user/42");
        assertEquals(1, hit.value);
        assertEquals(Map.of("id", "42"), hit.params);
    }

    @Test
    void matchAnyBindsMultipleParamsInOrder() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:x/:y", 1);
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/a/b");
        assertEquals(1, hit.value);
        assertEquals(Map.of("x", "a", "y", "b"), hit.params);
    }

    @Test
    void matchAnyPrefersLiteralOverParamInSameSlot() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RestRadixTree.MatchResult<Integer> literalHit = tree.matchAny("/user/admin");
        assertEquals(1, literalHit.value);
        assertEquals(Map.of(), literalHit.params);
    }

    @Test
    void matchAnyBacktracksFromDeadLiteralBranchToParamBranch() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/user/posts");
        assertEquals(2, hit.value);
        assertEquals(Map.of("param", "user"), hit.params);
    }

    @Test
    void matchAnySameNameParamPatternsEachHitWithOwnBinding() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:x/aaa/bbb", 1);
        tree.put("/:x/ccc/ddd", 2);
        RestRadixTree.MatchResult<Integer> first = tree.matchAny("/p/aaa/bbb");
        assertEquals(1, first.value);
        assertEquals(Map.of("x", "p"), first.params);
        RestRadixTree.MatchResult<Integer> second = tree.matchAny("/q/ccc/ddd");
        assertEquals(2, second.value);
        assertEquals(Map.of("x", "q"), second.params);
    }

    @Test
    void matchAnyFailedParamBindingLeavesNoResidueOnLaterHit() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:a/zzz", 1);
        tree.put("/lit/:b/end", 2);
        // 字面 lit 分支先试：绑定 b=zzz 走到无值节点失败，撤销后回溯根参数分支命中
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/lit/zzz");
        assertEquals(1, hit.value);
        assertEquals(Map.of("a", "lit"), hit.params, "失败分支的绑定不得残留");
        RestRadixTree.MatchResult<Integer> direct = tree.matchAny("/lit/other/end");
        assertEquals(2, direct.value);
        assertEquals(Map.of("b", "other"), direct.params);
    }

    @Test
    void matchAnyMissWhenSegmentsDontReachValue() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        tree.put("/b/:id", 2);
        assertNull(tree.matchAny("/a/c"), "字面节点无更深子节点");
        assertNull(tree.matchAny("/b"), "参数节点无值");
        assertNull(tree.matchAny("/b/id/extra"), "越过已注册深度");
    }

    @Test
    void matchAnyMissWhenMidPathDiverges() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        tree.put("/:x/aaa", 2);
        assertNull(tree.matchAny("/a/c"), "字面槽位失配且无参数分支");
        assertNull(tree.matchAny("/z/bbb"), "参数链下字面失配");
        assertNull(tree.matchAny("/x/aaa/extra"), "命中深度多出一段");
    }

    @Test
    void matchAnyMissAfterBacktrackExhaustedReturnsNull() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:param/aaa", 1);
        assertNull(tree.matchAny("/x/bbb"));
    }

    @Test
    void matchAnyNormalizesRedundantSlashes() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        assertEquals(1, tree.matchAny("//a/b//").value, "首尾多余斜杠");
        assertEquals(1, tree.matchAny("/a//b").value, "中间连续斜杠");
        assertEquals(1, tree.matchAny("///a/b///").value, "首尾与中间叠加");
    }

    @Test
    void matchAnyOnRootOnlyPathReturnsNull() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        assertNull(tree.matchAny("/"));
        assertNull(tree.matchAny("//"));
    }

    @Test
    void matchAnyPathWithoutLeadingSlashReturnsNull() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        assertNull(tree.matchAny("a"));
        assertNull(tree.matchAny("a/b"));
        assertNull(tree.matchAny(""));
    }

    @Test
    void matchAnyParamsAreImmutable() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/:id", 1);
        RestRadixTree.MatchResult<Integer> hit = tree.matchAny("/user/42");
        assertThrows(UnsupportedOperationException.class, () -> hit.params.put("k", "v"));
    }

    // ---------- 不支持的读方法 ----------

    @Test
    void unsupportedReadMethodsThrow() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        assertEquals("The rest radix tree does not support the get operation",
                assertThrows(UnsupportedOperationException.class, () -> tree.get("/a")).getMessage());
        assertEquals("The rest radix tree does not support the exist operation",
                assertThrows(UnsupportedOperationException.class, () -> tree.exist("/a")).getMessage());
        assertEquals("The rest radix tree does not support the match operation",
                assertThrows(UnsupportedOperationException.class, () -> tree.match("/a")).getMessage());
    }

    // ---------- remove 折叠 ----------

    @Test
    void removeLeafLiteralDetachesUpToRoot() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        assertTrue(tree.remove("/a/b"));
        assertEquals("[=null]", dump(tree), "无值无子的整条链应折叠殆尽");
    }

    @Test
    void removeFoldStopsAtValuedAncestor() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        tree.put("/a/b/c", 2);
        assertTrue(tree.remove("/a/b/c"));
        assertEquals("[=null [a=null [b=1]]]", dump(tree));
    }

    @Test
    void removeFoldStopsAtAncestorWithRemainingChild() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b/c", 1);
        tree.put("/a/d", 2);
        assertTrue(tree.remove("/a/b/c"));
        assertEquals("[=null [a=null [d=2]]]", dump(tree));
    }

    @Test
    void removeParamLeafDetachesFromParent() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:x/a", 1);
        tree.put("/:x/b", 2);
        assertTrue(tree.remove("/:x/a"));
        assertEquals("[=null [:x=null [b=2]]]", dump(tree));
        assertTrue(tree.remove("/:x/b"));
        assertEquals("[=null]", dump(tree), "参数节点与参数链应一并折叠");
    }

    @Test
    void removeKeepsValuedParamSiblingBranch() throws Exception {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:x/a", 1);
        tree.put("/lit", 2);
        assertTrue(tree.remove("/:x/a"));
        assertEquals("[=null [lit=2]]", dump(tree));
    }

    @Test
    void removeRegisteredParamPatternRequiresSameName() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/:x/a", 1);
        assertFalse(tree.remove("/:y/a"), "同槽参数名不同即未注册");
        assertTrue(tree.remove("/:x/a"));
    }

    @Test
    void removeTwiceReturnsFalseOnSecondCall() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        assertTrue(tree.remove("/a"));
        assertFalse(tree.remove("/a"));
    }

    @Test
    void removeThenMatchMissesAndReputReuses() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a/b", 1);
        assertTrue(tree.remove("/a/b"));
        assertNull(tree.matchAny("/a/b"));
        assertNull(tree.put("/a/b", 2));
        assertEquals(2, tree.matchAny("/a/b").value);
    }

    // ---------- 结构 dump ----------

    private static String dump(RestRadixTree<?> tree) throws Exception {
        Field rootField = RestRadixTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        return dumpNode("", rootField.get(tree));
    }

    private static String dumpNode(String literalKey, Object node) throws Exception {
        Object value = field(node, "value").get(node);
        @SuppressWarnings("unchecked")
        Map<String, Object> literals = (Map<String, Object>) field(node, "literals").get(node);
        Object paramChild = field(node, "paramChild").get(node);
        String paramName = (String) field(node, "paramName").get(node);
        // 段名取自身：参数节点记 ":name"，字面节点记父方的键，根节点为空串
        String label = paramName != null ? ":" + paramName : literalKey;
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(label).append('=').append(value);
        List<String> keys = new ArrayList<>(literals.keySet());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            sb.append(' ').append(dumpNode(key, literals.get(key)));
        }
        if (paramChild != null) {
            sb.append(' ').append(dumpNode(null, paramChild));
        }
        return sb.append(']').toString();
    }

    private static Field field(Object node, String name) throws Exception {
        Field f = node.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
