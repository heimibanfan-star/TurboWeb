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
 * {@link RestRouteTree} 的 put 分类、put / remove 校验全表、remove miss 基线、
 * matchAny 回溯匹配与 match 单命中委托。
 * <p>结构断言经反射读取节点字段，dump 形如 [seg=value [子节点]]，
 * 字面子节点按键排序、参数子节点按参数名排序殿后且段名记 ":name"；根节点段为空、值恒为 null。
 */
class RestRouteTreeTest {

    // ---------- put ----------

    @Test
    void putOnEmptyTreeCreatesLiteralChain() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertEquals("[=null [a=null [b=1]]]", dump(tree));
    }

    @Test
    void putRejectsDuplicateRegistrationAndKeepsOriginalValue() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertEquals("Path pattern '/a/b' is already registered",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/b", 2)).getMessage());
        assertEquals(1, tree.matchAny("/a/b").value(), "重复注册拒收后原值保持不变");
        assertEquals("[=null [a=null [b=1]]]", dump(tree));
    }

    @Test
    void putRejectsDuplicateParamPatternAndKeepsOriginalValue() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/x/:id", 1);
        assertEquals("Path pattern '/x/:id' is already registered",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/x/:id", 2)).getMessage());
        assertEquals(1, tree.matchAny("/x/9").value(), "重复注册拒收后原值与参数绑定保持不变");
        assertEquals("[=null [x=null [:id=1]]]", dump(tree));
    }

    @Test
    void putLiteralAndParamCoexistInSameSlotWithLiteralFirst() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        tree.put("/:x", 2);
        assertEquals("[=null [a=1] [:x=2]]", dump(tree));
    }

    @Test
    void putLiteralAndParamChainsContinueBelowParamNode() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/a", 1);
        tree.put("/:x/:y", 2);
        assertEquals("[=null [:x=null [a=1] [:y=2]]]", dump(tree));
    }

    @Test
    void putSameNameParamsInDifferentPatternsShareParamNode() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/aaa/bbb", 1);
        tree.put("/:x/ccc/ddd", 2);
        assertEquals("[=null [:x=null [aaa=null [bbb=1]] [ccc=null [ddd=2]]]]", dump(tree));
    }

    @Test
    void putLiteralsSortedAndParamChildLast() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/b", 1);
        tree.put("/a", 2);
        tree.put("/:x", 3);
        assertEquals("[=null [a=2] [b=1] [:x=3]]", dump(tree));
    }

    // ---------- put 校验全表 ----------

    @Test
    void putRejectsNullKey() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put(null, 1)).getMessage());
    }

    @Test
    void putChecksValueBeforeLeadingSlash() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("abc", null)).getMessage());
    }

    @Test
    void putRejectsNullValue() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a", null)).getMessage());
    }

    @Test
    void putRejectsPatternWithoutLeadingSlash() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("abc", 1)).getMessage());
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("", 1)).getMessage());
    }

    @Test
    void putRejectsEmptySegments() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
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
        RestRouteTree<Integer> tree = new RestRouteTree<>();
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
    void putRejectsColonInsideLiteralSegment() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Literal path segment must not contain ':': aaa:param",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/aaa:param", 1)).getMessage());
        assertEquals("Literal path segment must not contain ':': a:b",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a:b/c", 1)).getMessage());
    }

    @Test
    void putRejectsParamNameContainingColon() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Invalid path variable: :param:param",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:param:param", 1)).getMessage());
        assertEquals("Invalid path variable: :y:z",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/x/:y:z", 1)).getMessage());
    }

    @Test
    void putAcceptsValidParamNameBoundaries() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:a", 1);
        tree.put("/b/:_x1", 2);
        tree.put("/c/:A9_", 3);
        assertEquals("[=null [b=null [:_x1=2]] [c=null [:A9_=3]] [:a=1]]", dump(tree));
    }

    @Test
    void putRejectsDuplicateParamNameInPattern() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Duplicate path variable 'x' in path",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:x/b/:x", 1)).getMessage());
    }

    @Test
    void putRejectsAmbiguousParamPatternsAndKeepsTreeUnchanged() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/path/:param1", 1);
        assertEquals("Ambiguous path pattern '/path/:param2' conflicts with existing pattern '/path/:param1'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/path/:param2", 2)).getMessage());
        assertEquals("[=null [path=null [:param1=1]]]", dump(tree));
    }

    @Test
    void putParamSiblingsWithDivergingSuffixesCoexist() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:param1/b", 1);
        tree.put("/a/:param2/c", 2);
        RouteTree.Result<Integer> first = tree.matchAny("/a/x/b");
        assertEquals(1, first.value());
        assertEquals(Map.of("param1", "x"), first.params());
        RouteTree.Result<Integer> second = tree.matchAny("/a/y/c");
        assertEquals(2, second.value());
        assertEquals(Map.of("param2", "y"), second.params());
        assertNull(tree.matchAny("/a/z/d"), "后缀分叉之外的路径不命中");
    }

    @Test
    void putRejectsAmbiguousSameSuffixParamPatternsAndKeepsTreeUnchanged() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:p/b", 1);
        assertEquals("Ambiguous path pattern '/a/:q/b' conflicts with existing pattern '/a/:p/b'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:q/b", 2)).getMessage());
        assertEquals("[=null [a=null [:p=null [b=1]]]]", dump(tree));
    }

    @Test
    void putConflictMessageRebuildsOnlyTheMatchingParamBranch() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:p/b", 1);
        tree.put("/:q/c", 2);
        // 先走 p 分支（后缀失配无冲突，回溯撤销）再走 q 分支命中冲突：
        // 重建的既有模式串不得残留已回溯分支的前缀
        assertEquals("Ambiguous path pattern '/:r/c' conflicts with existing pattern '/:q/c'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:r/c", 3)).getMessage());
    }

    @Test
    void putParamAndLiteralPrefixesAcrossSlotsCoexist() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/a", 1);
        tree.put("/b/:y", 2);
        RouteTree.Result<Integer> paramFirst = tree.matchAny("/c/a");
        assertEquals(1, paramFirst.value());
        assertEquals(Map.of("x", "c"), paramFirst.params());
        RouteTree.Result<Integer> literalFirst = tree.matchAny("/b/z");
        assertEquals(2, literalFirst.value());
        assertEquals(Map.of("y", "z"), literalFirst.params());
        assertEquals(2, tree.matchAny("/b/a").value(), "字面分支优先命中");
        assertEquals(Map.of("y", "a"), tree.matchAny("/b/a").params());
    }

    @Test
    void putPrefixPatternOfValuedLongerPatternCoexists() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b/c", 1);
        tree.put("/a/b", 2);
        tree.put("/a", 3);
        assertEquals("[=null [a=3 [b=2 [c=1]]]]", dump(tree));
        assertEquals(1, tree.matchAny("/a/b/c").value());
        assertEquals(2, tree.matchAny("/a/b").value());
        assertEquals(3, tree.matchAny("/a").value());
    }

    @Test
    void putParamMidValueWithDeeperChildCoexists() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x", 1);
        tree.put("/:x/b", 2);
        assertEquals("[=null [:x=1 [b=2]]]", dump(tree));
        assertEquals(1, tree.matchAny("/a").value());
        assertEquals(2, tree.matchAny("/a/b").value());
    }

    @Test
    void putRejectsAmbiguousParamNameCaseSensitive() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x", 1);
        assertEquals("Ambiguous path pattern '/:X' conflicts with existing pattern '/:x'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/:X", 2)).getMessage());
        assertEquals("[=null [:x=1]]", dump(tree), "参数名大小写敏感，仅同名判重复");
    }

    @Test
    void putSameNameParamAtDifferentDepthsCoexist() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:x", 1);
        tree.put("/b/:x", 2);
        assertEquals(Map.of("x", "p"), tree.matchAny("/a/p").params());
        assertEquals(Map.of("x", "q"), tree.matchAny("/b/q").params());
    }

    @Test
    void putAmbiguousDeepAlignmentRebuildsFullPathInMessage() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:p/b/:q", 1);
        assertEquals("Ambiguous path pattern '/a/:r/b/:s' conflicts with existing pattern '/a/:p/b/:q'",
                assertThrows(IllegalArgumentException.class, () -> tree.put("/a/:r/b/:s", 2)).getMessage());
    }

    @Test
    void removeLiftsConflictSoSameSlotParamReRegisters() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:q/c", 1);
        assertThrows(IllegalArgumentException.class, () -> tree.put("/:r/c", 2));
        assertTrue(tree.remove("/:q/c"));
        tree.put("/:r/c", 3);
        assertEquals(3, tree.matchAny("/t/c").value());
    }

    // ---------- remove 校验与 miss 基线 ----------

    @Test
    void removeRejectsNullKey() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.remove(null)).getMessage());
    }

    @Test
    void removeRejectsPatternWithoutLeadingSlash() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The key must start with '/'",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("abc")).getMessage());
    }

    @Test
    void removeRejectsEmptySegments() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a//b")).getMessage());
        assertEquals("Empty path segment is not allowed",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a/")).getMessage());
    }

    @Test
    void removeRejectsInvalidParamSegment() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Invalid path variable: :1abc",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/:1abc")).getMessage());
    }

    @Test
    void removeRejectsColonInsideLiteralSegment() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Literal path segment must not contain ':': aaa:param",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/aaa:param")).getMessage());
    }

    @Test
    void removeRejectsDuplicateParamNameInPattern() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("Duplicate path variable 'x' in path",
                assertThrows(IllegalArgumentException.class, () -> tree.remove("/a/:x/b/:x")).getMessage());
    }

    @Test
    void removeMissOnEmptyTreeReturnsFalse() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertFalse(tree.remove("/a"), "字面分支不存在");
        assertFalse(tree.remove("/:x"), "参数分支不存在");
    }

    @Test
    void removeMissOnAbsentBranchReturnsFalse() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
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
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The path argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.matchAny(null)).getMessage());
    }

    @Test
    void matchAnyOnEmptyTreeReturnsNull() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertNull(tree.matchAny("/a"));
    }

    @Test
    void matchAnyHitsLiteralPatternWithoutParams() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/a/b");
        assertEquals(1, hit.value());
        assertEquals(Map.of(), hit.params());
    }

    @Test
    void matchAnyBindsSingleParam() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/:id", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/user/42");
        assertEquals(1, hit.value());
        assertEquals(Map.of("id", "42"), hit.params());
    }

    @Test
    void matchAnyBindsMultipleParamsInOrder() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/:y", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/a/b");
        assertEquals(1, hit.value());
        assertEquals(Map.of("x", "a", "y", "b"), hit.params());
    }

    @Test
    void matchAnyPrefersLiteralOverParamInSameSlot() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RouteTree.Result<Integer> literalHit = tree.matchAny("/user/admin");
        assertEquals(1, literalHit.value());
        assertEquals(Map.of(), literalHit.params());
    }

    @Test
    void matchAnyBacktracksFromDeadLiteralBranchToParamBranch() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RouteTree.Result<Integer> hit = tree.matchAny("/user/posts");
        assertEquals(2, hit.value());
        assertEquals(Map.of("param", "user"), hit.params());
    }

    @Test
    void matchAnySameNameParamPatternsEachHitWithOwnBinding() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/aaa/bbb", 1);
        tree.put("/:x/ccc/ddd", 2);
        RouteTree.Result<Integer> first = tree.matchAny("/p/aaa/bbb");
        assertEquals(1, first.value());
        assertEquals(Map.of("x", "p"), first.params());
        RouteTree.Result<Integer> second = tree.matchAny("/q/ccc/ddd");
        assertEquals(2, second.value());
        assertEquals(Map.of("x", "q"), second.params());
    }

    @Test
    void matchAnyFailedParamBindingLeavesNoResidueOnLaterHit() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:a/zzz", 1);
        tree.put("/lit/:b/end", 2);
        // 字面 lit 分支先试：绑定 b=zzz 走到无值节点失败，撤销后回溯根参数分支命中
        RouteTree.Result<Integer> hit = tree.matchAny("/lit/zzz");
        assertEquals(1, hit.value());
        assertEquals(Map.of("a", "lit"), hit.params(), "失败分支的绑定不得残留");
        RouteTree.Result<Integer> direct = tree.matchAny("/lit/other/end");
        assertEquals(2, direct.value());
        assertEquals(Map.of("b", "other"), direct.params());
    }

    @Test
    void matchAnyBacktracksAcrossParamSiblingBranches() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:x/b", 1);
        tree.put("/a/:y", 2);
        RouteTree.Result<Integer> deep = tree.matchAny("/a/p/b");
        assertEquals(1, deep.value());
        assertEquals(Map.of("x", "p"), deep.params());
        RouteTree.Result<Integer> shallow = tree.matchAny("/a/q");
        assertEquals(2, shallow.value(), "参数分支逐支回溯命中");
        assertEquals(Map.of("y", "q"), shallow.params());
        assertNull(tree.matchAny("/a/r/c"), "两参数分支后缀之外不命中");
    }

    @Test
    void matchAnyDeepLiteralStillWinsOverParamSibling() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b/c", 1);
        tree.put("/a/:x/c", 2);
        RouteTree.Result<Integer> literal = tree.matchAny("/a/b/c");
        assertEquals(1, literal.value());
        assertEquals(Map.of(), literal.params());
        RouteTree.Result<Integer> param = tree.matchAny("/a/z/c");
        assertEquals(2, param.value());
        assertEquals(Map.of("x", "z"), param.params());
    }

    @Test
    void matchAnySortsHitsBySignatureNotByDfsOrder() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        // 根参数分支 HashMap 遍历序 x 在 y 先：DFS 先收集签名 11 的命中，后收集签名 10 的命中
        tree.put("/:x/:w", 1);
        tree.put("/:y/b", 2);
        RouteTree.Result<Integer> hit = tree.matchAny("/t/b");
        assertEquals(2, hit.value(), "按签名取最小而非 DFS 首命中");
        assertEquals(Map.of("y", "t"), hit.params());
    }

    @Test
    void matchAnyMissWhenSegmentsDontReachValue() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        tree.put("/b/:id", 2);
        assertNull(tree.matchAny("/a/c"), "字面节点无更深子节点");
        assertNull(tree.matchAny("/b"), "参数节点无值");
        assertNull(tree.matchAny("/b/id/extra"), "越过已注册深度");
    }

    @Test
    void matchAnyMissWhenMidPathDiverges() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        tree.put("/:x/aaa", 2);
        assertNull(tree.matchAny("/a/c"), "字面槽位失配且无参数分支");
        assertNull(tree.matchAny("/z/bbb"), "参数链下字面失配");
        assertNull(tree.matchAny("/x/aaa/extra"), "命中深度多出一段");
    }

    @Test
    void matchAnyMissAfterBacktrackExhaustedReturnsNull() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:param/aaa", 1);
        assertNull(tree.matchAny("/x/bbb"));
    }

    @Test
    void matchAnyNormalizesRedundantSlashes() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertEquals(1, tree.matchAny("//a/b//").value(), "首尾多余斜杠");
        assertEquals(1, tree.matchAny("/a//b").value(), "中间连续斜杠");
        assertEquals(1, tree.matchAny("///a/b///").value(), "首尾与中间叠加");
    }

    @Test
    void matchAnyOnRootOnlyPathReturnsNull() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        assertNull(tree.matchAny("/"));
        assertNull(tree.matchAny("//"));
    }

    @Test
    void matchAnyPathWithoutLeadingSlashReturnsNull() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        assertNull(tree.matchAny("a"));
        assertNull(tree.matchAny("a/b"));
        assertNull(tree.matchAny(""));
    }

    @Test
    void matchAnyParamsAreImmutable() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/:id", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/user/42");
        assertThrows(UnsupportedOperationException.class, () -> hit.params().put("k", "v"));
    }

    @Test
    void matchAnyBindsSegmentContainingColonToParam() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/:id", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/user/a:b");
        assertEquals(1, hit.value());
        assertEquals(Map.of("id", "a:b"), hit.params(), "匹配侧不校验冒号，段原文绑定为参数值");
    }

    @Test
    void matchAnyBindsColonLedSegmentAsParamValue() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x", 1);
        RouteTree.Result<Integer> hit = tree.matchAny("/:x");
        assertEquals(1, hit.value());
        assertEquals(Map.of("x", ":x"), hit.params(), "路径段 :x 无字面节点，落参数分支绑定段原文");
    }

    @Test
    void putAndMatchFourSegmentPattern() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:x/c/:y", 1);
        assertEquals(1, tree.matchAny("/a/b/c/d").value());
        assertEquals(Map.of("x", "b", "y", "d"), tree.matchAny("/a/b/c/d").params());
        assertNull(tree.matchAny("/a/b/c"), "不足段数不命中");
        assertNull(tree.matchAny("/a/b/c/d/e"), "超出段数不命中");
    }

    @Test
    void matchAnyTrailingSlashNormalizesToRegisteredPrefix() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        assertEquals(1, tree.matchAny("/a/").value(), "尾斜杠归一命中");
    }

    // ---------- get / exist 精确匹配 ----------

    @Test
    void getAndExistHitLiteralPatternWithEmptyParams() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        RouteTree.Result<Integer> hit = tree.get("/a/b");
        assertEquals(1, hit.value());
        assertEquals(Map.of(), hit.params(), "精确字面命中无参数绑定");
        assertTrue(tree.exist("/a/b"));
    }

    @Test
    void getRetrievesParamPatternByExactTextOnly() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:b/a", 1);
        RouteTree.Result<Integer> hit = tree.get("/a/:b/a");
        assertEquals(1, hit.value(), "冒号段作为普通字符串与注册模式逐段比对");
        assertEquals(Map.of(), hit.params(), "精确取值不做参数绑定");
        assertTrue(tree.exist("/a/:b/a"));
        assertNull(tree.get("/a/x/a"), "不做通配：普通段不走参数边");
        assertFalse(tree.exist("/a/x/a"));
        assertNull(tree.get("/a/:c/a"), "参数段异名即未注册");
        assertEquals(1, tree.matchAny("/a/x/a").value(), "含参模式仍经 matchAny 生效");
    }

    @Test
    void getMissCasesReturnNullAndFalse() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertNull(tree.get("/a"), "中间无值节点不算命中");
        assertNull(tree.get("/a/b/c"), "超段数不命中");
        assertNull(tree.get("/x/y"), "未注册路径不命中");
        assertFalse(tree.exist("/a"));
        assertFalse(tree.exist("/x/y"));
    }

    @Test
    void getNormalizesLikeMatchAny() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertEquals(1, tree.get("/a//b").value(), "连续斜杠归一后视同单斜杠");
        assertEquals(1, tree.get("/a/b/").value(), "尾斜杠归一命中");
        assertNull(tree.get("a/b"), "无前导斜杠按未命中处理");
        assertNull(tree.get("/"), "零段不命中");
        assertFalse(tree.exist("a/b"));
    }

    @Test
    void getAndExistRejectNullKey() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.get(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.exist(null)).getMessage());
    }

    // ---------- match 单命中委托 ----------

    @Test
    void matchSingleHitDelegatesToMatchAny() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/:id", 1);
        RouteTree.Result<Integer> hit = tree.match("/user/42");
        assertEquals(1, hit.value(), "match 委托 matchAny，命中与择优同口径");
        assertEquals(Map.of("id", "42"), hit.params());
        assertNull(tree.match("/nope"), "无命中返回 null");
    }

    @Test
    void matchEqualsMatchAnyAcrossEdgePaths() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        tree.put("/user/:id", 2);
        tree.put("/:x/:y/b", 3);
        for (String path : List.of("/a/b", "/a/b/", "//a//b//", "/user/42", "/user/42/",
                "/x/y/b", "/x/y", "/", "//", "/a", "/a/c", "a/b", "", "/:x/:y/b")) {
            assertEquals(tree.matchAny(path), tree.match(path),
                    "match 与 matchAny 逐路径等值, path=" + path);
        }
        assertEquals("The path argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.match(null)).getMessage());
    }

    @Test
    void matchEqualsMatchAnyWithFastPathEnabled() {
        RestRouteTree<Integer> tree = new RestRouteTree<>(true);
        tree.put("/a", 1);
        tree.put("/:x/b", 2);
        for (String path : List.of("/a", "/a/", "//a//", "/q/b", "/q", "/x/b", "/nope", "/")) {
            assertEquals(tree.matchAny(path), tree.match(path),
                    "开启快表后 match 仍与 matchAny 逐路径等值, path=" + path);
        }
    }

    @Test
    void matchResultIsRecordValuedWithImmutableParams() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/user/:id", 1);
        RouteTree.Result<Integer> hit = tree.match("/user/42");
        assertThrows(UnsupportedOperationException.class, () -> hit.params().put("k", "v"),
                "match 侧结果参数绑定同样不可变");
        assertEquals(hit, tree.matchAny("/user/42"), "记录按值相等，两通道结果无差别");
    }

    // ---------- remove 折叠 ----------

    @Test
    void removeLeafLiteralDetachesUpToRoot() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertTrue(tree.remove("/a/b"));
        assertEquals("[=null]", dump(tree), "无值无子的整条链应折叠殆尽");
    }

    @Test
    void removeFoldStopsAtValuedAncestor() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        tree.put("/a/b/c", 2);
        assertTrue(tree.remove("/a/b/c"));
        assertEquals("[=null [a=null [b=1]]]", dump(tree));
    }

    @Test
    void removeFoldStopsAtAncestorWithRemainingChild() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b/c", 1);
        tree.put("/a/d", 2);
        assertTrue(tree.remove("/a/b/c"));
        assertEquals("[=null [a=null [d=2]]]", dump(tree));
    }

    @Test
    void removeParamLeafDetachesFromParent() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/a", 1);
        tree.put("/:x/b", 2);
        assertTrue(tree.remove("/:x/a"));
        assertEquals("[=null [:x=null [b=2]]]", dump(tree));
        assertTrue(tree.remove("/:x/b"));
        assertEquals("[=null]", dump(tree), "参数节点与参数链应一并折叠");
    }

    @Test
    void removeKeepsValuedParamSiblingBranch() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/a", 1);
        tree.put("/lit", 2);
        assertTrue(tree.remove("/:x/a"));
        assertEquals("[=null [lit=2]]", dump(tree));
    }

    @Test
    void removeParamSiblingFoldsOnlyItsOwnBranch() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/:x/b", 1);
        tree.put("/a/:y/c", 2);
        assertTrue(tree.remove("/a/:x/b"));
        assertNull(tree.matchAny("/a/p/b"), "被摘分支不再命中");
        assertEquals(2, tree.matchAny("/a/q/c").value(), "兄弟参数分支保留");
        assertTrue(tree.remove("/a/:y/c"));
        assertEquals("[=null]", dump(tree), "两条参数分支应折叠殆尽");
    }

    @Test
    void removeRegisteredParamPatternRequiresSameName() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x/a", 1);
        assertFalse(tree.remove("/:y/a"), "同槽参数名不同即未注册");
        assertTrue(tree.remove("/:x/a"));
    }

    @Test
    void removeTwiceReturnsFalseOnSecondCall() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a", 1);
        assertTrue(tree.remove("/a"));
        assertFalse(tree.remove("/a"));
    }

    @Test
    void removeParamMidValueKeepsDeeperChild() throws Exception {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/:x", 1);
        tree.put("/:x/b", 2);
        assertTrue(tree.remove("/:x"));
        assertEquals("[=null [:x=null [b=2]]]", dump(tree));
        assertEquals(2, tree.matchAny("/a/b").value());
        assertNull(tree.matchAny("/a"), "参数节点自身值已摘除");
    }

    @Test
    void removeThenMatchMissesAndReputReuses() {
        RestRouteTree<Integer> tree = new RestRouteTree<>();
        tree.put("/a/b", 1);
        assertTrue(tree.remove("/a/b"));
        assertNull(tree.matchAny("/a/b"));
        tree.put("/a/b", 2);
        assertEquals(2, tree.matchAny("/a/b").value());
    }

    // ---------- 结构 dump ----------

    private static String dump(RestRouteTree<?> tree) throws Exception {
        Field rootField = RestRouteTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        return dumpNode("", rootField.get(tree));
    }

    private static String dumpNode(String label, Object node) throws Exception {
        Object value = field(node, "value").get(node);
        @SuppressWarnings("unchecked")
        Map<String, Object> literals = (Map<String, Object>) field(node, "literals").get(node);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) field(node, "params").get(node);
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(label).append('=').append(value);
        List<String> keys = new ArrayList<>(literals.keySet());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            sb.append(' ').append(dumpNode(key, literals.get(key)));
        }
        List<String> paramNames = new ArrayList<>(params.keySet());
        paramNames.sort(Comparator.naturalOrder());
        for (String name : paramNames) {
            sb.append(' ').append(dumpNode(":" + name, params.get(name)));
        }
        return sb.append(']').toString();
    }

    private static Field field(Object node, String name) throws Exception {
        Field f = node.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
