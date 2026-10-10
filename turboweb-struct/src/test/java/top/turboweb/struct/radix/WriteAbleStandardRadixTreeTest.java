package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WriteAbleStandardRadixTree} 的 put 四分类、remove 三分支与父合并、
 * 三个读方法行为及参数校验。
 * <p>结构断言经反射读取节点字段，dump 形如 [edge=value [子节点] [子节点]]，
 * 子节点按入边首字符排序；根节点 edge 恒为空串、value 恒为 null。
 */
class WriteAbleStandardRadixTreeTest {

    // ---------- put ----------

    @Test
    void putOnEmptyTreeCreatesSingleChild() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertNull(tree.put("abc", 1));
        assertEquals("[=null [abc=1]]", dump(tree));
        assertEquals(1, tree.get("abc"));
        assertTrue(tree.exist("abc"));
    }

    @Test
    void putExactMatchOverwritesAndReturnsOldValue() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertNull(tree.put("k", 1));
        assertEquals(1, tree.put("k", 2));
        assertEquals("[=null [k=2]]", dump(tree));
        assertEquals(2, tree.get("k"));
    }

    @Test
    void putDeepExactMatchOverwritesAndReturnsOldValue() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abcd", 2);
        assertEquals(2, tree.put("abcd", 9));
        assertEquals("[=null [ab=1 [cd=9]]]", dump(tree));
        assertEquals(9, tree.get("abcd"));
    }

    @Test
    void putKeyEndingMidEdgeSplitsIntoTwo() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        assertNull(tree.put("ab", 2));
        assertEquals("[=null [ab=2 [c=1]]]", dump(tree));
        assertEquals(2, tree.get("ab"));
        assertEquals(1, tree.get("abc"));
        assertTrue(tree.exist("abc"));
        assertEquals(Set.of(2, 1), tree.match("abcd"));
    }

    @Test
    void putAppendsNewChildAfterFullyMatchedEdge() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        assertNull(tree.put("abcd", 2));
        assertEquals("[=null [ab=1 [cd=2]]]", dump(tree));
        assertEquals(1, tree.get("ab"));
        assertEquals(2, tree.get("abcd"));
    }

    @Test
    void putDivergingMidEdgeSplitsIntoThree() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        assertNull(tree.put("abd", 2));
        assertEquals("[=null [ab=null [c=1] [d=2]]]", dump(tree));
        assertEquals(1, tree.get("abc"));
        assertEquals(2, tree.get("abd"));
        assertFalse(tree.exist("ab"));
        assertEquals(Set.of(), tree.match("abe"));
    }

    @Test
    void putDivergingBelowNestedEdgeSplitsIntoThree() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abcd", 2);
        assertNull(tree.put("abce", 3));
        assertEquals("[=null [ab=1 [c=null [d=2] [e=3]]]]", dump(tree));
        assertEquals(2, tree.get("abcd"));
        assertEquals(3, tree.get("abce"));
        assertNull(tree.put("abcde", 4));
        assertEquals("[=null [ab=1 [c=null [d=2 [e=4]] [e=3]]]]", dump(tree));
        assertEquals(4, tree.get("abcde"));
    }

    // ---------- remove ----------

    @Test
    void removeLeafWithValuedParentKeepsParent() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abc", 2);
        assertTrue(tree.remove("abc"));
        assertEquals("[=null [ab=1]]", dump(tree));
        assertEquals(1, tree.get("ab"));
        assertFalse(tree.exist("abc"));
    }

    @Test
    void removeLeafTriggersParentMergeWhenParentValueless() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        tree.put("abd", 2);
        assertTrue(tree.remove("abc"));
        assertEquals("[=null [abd=2]]", dump(tree));
        assertEquals(2, tree.get("abd"));
        assertFalse(tree.exist("abc"));
        assertEquals(Set.of(2), tree.match("abd"));
        assertEquals(Set.of(), tree.match("abc"));
    }

    @Test
    void removeNodeWithSingleChildMergesWithChild() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abc", 2);
        assertTrue(tree.remove("ab"));
        assertEquals("[=null [abc=2]]", dump(tree));
        assertEquals(2, tree.get("abc"));
        assertFalse(tree.exist("ab"));
        assertEquals(Set.of(2), tree.match("abcd"));
    }

    @Test
    void removeNodeWithMultipleChildrenOnlyClearsValue() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abc", 2);
        tree.put("abd", 3);
        assertTrue(tree.remove("ab"));
        assertEquals("[=null [ab=null [c=2] [d=3]]]", dump(tree));
        assertEquals(2, tree.get("abc"));
        assertEquals(3, tree.get("abd"));
        assertFalse(tree.exist("ab"));
        assertEquals(Set.of(), tree.match("ab"));
        assertEquals(Set.of(3), tree.match("abd"));
    }

    @Test
    void removeFromRootDoesNotMergeRootWithRemainingChild() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("a", 1);
        tree.put("b", 2);
        assertTrue(tree.remove("a"));
        assertEquals("[=null [b=2]]", dump(tree));
        assertEquals(2, tree.get("b"));
        assertEquals(Set.of(2), tree.match("b"));
        assertEquals(Set.of(), tree.match("a"));
    }

    @Test
    void removeMissesReturnFalse() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertFalse(tree.remove("x"), "空树无键可删");
        tree.put("abc", 1);
        tree.put("abd", 2);
        assertFalse(tree.remove("z"), "首字符无子节点");
        assertFalse(tree.remove(""), "零长度键停在根，根无值");
        assertFalse(tree.remove("a"), "key 短于入边");
        assertFalse(tree.remove("ab"), "路径节点无值");
        assertFalse(tree.remove("axe"), "分叉后无子节点");
    }

    @Test
    void removeKeyEndingMidEdgeReturnsFalse() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        assertFalse(tree.remove("ab"));
    }

    @Test
    void putThenRemoveRestoresEmptyTree() throws Exception {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        tree.put("abd", 2);
        tree.put("abe", 3);
        assertTrue(tree.remove("abc"));
        assertEquals("[=null [ab=null [d=2] [e=3]]]", dump(tree));
        assertTrue(tree.remove("abd"));
        assertEquals("[=null [abe=3]]", dump(tree));
        assertTrue(tree.remove("abe"));
        assertEquals("[=null]", dump(tree));
        assertFalse(tree.exist("abc"));
        assertFalse(tree.exist("abd"));
        assertFalse(tree.exist("abe"));
        assertEquals(Set.of(), tree.match("abc"));
    }

    // ---------- get / exist ----------

    @Test
    void getMissesReturnNull() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        tree.put("abd", 2);
        assertNull(tree.get("ab"), "路径节点无值");
        assertNull(tree.get("abe"), "分叉后无子节点");
        assertNull(tree.get("ax"), "边内字符分叉");
        assertNull(tree.get("z"), "首字符无子节点");
        assertNull(tree.get(""), "零长度键停在根，根无值");
        assertNull(tree.get("a"), "key 短于入边");
    }

    @Test
    void existReflectsRegisteredKeysOnly() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        tree.put("abd", 2);
        assertTrue(tree.exist("abc"));
        assertTrue(tree.exist("abd"));
        assertFalse(tree.exist("ab"));
        assertFalse(tree.exist(""));
        assertFalse(tree.exist("abcd"));
    }

    // ---------- match ----------

    @Test
    void matchExactKeyIncludesItsOwnValue() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        assertEquals(Set.of(1), tree.match("abc"));
    }

    @Test
    void matchCollectsAllRegisteredPrefixValues() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("a", 1);
        tree.put("abc", 2);
        assertEquals(Set.of(1, 2), tree.match("abcd"));
        assertEquals(Set.of(1), tree.match("ab"));
        assertEquals(Set.of(1), tree.match("a"));
        assertEquals(Set.of(), tree.match("b"));
    }

    @Test
    void matchKeyEndingMidEdgeCollectsNothing() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        assertEquals(Set.of(), tree.match("ab"));
    }

    @Test
    void matchKeyEndingOnValuelessNodeCollectsNothing() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", 1);
        tree.put("abd", 2);
        assertEquals(Set.of(), tree.match("ab"));
    }

    @Test
    void matchDivergenceKeepsAncestorHitsOnly() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        tree.put("ab", 1);
        tree.put("abc", 2);
        assertEquals(Set.of(1), tree.match("abd"), "分叉前祖先已命中");
        assertEquals(Set.of(), tree.match("ax"), "边内分叉无命中");
    }

    @Test
    void matchOnEmptyTreeReturnsEmptySet() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals(Set.of(), tree.match("abc"));
    }

    @Test
    void matchDedupesEqualValuesFromDifferentKeys() {
        WriteAbleStandardRadixTree<String> tree = new WriteAbleStandardRadixTree<>();
        tree.put("a", "v");
        tree.put("ab", "v");
        assertEquals(Set.of("v"), tree.match("abc"));
    }

    // ---------- 校验 ----------

    @Test
    void putRejectsNullKey() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put(null, 1)).getMessage());
    }

    @Test
    void putRejectsZeroLengthKey() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals("The key argument was zero-length",
                assertThrows(IllegalArgumentException.class, () -> tree.put("", 1)).getMessage());
    }

    @Test
    void putRejectsNullValue() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals("The value argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.put("k", null)).getMessage());
    }

    @Test
    void removeRejectsNullKey() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.remove(null)).getMessage());
    }

    @Test
    void readsRejectNullKey() {
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.get(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.exist(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> tree.match(null)).getMessage());
    }

    // ---------- 结构 dump ----------

    private static String dump(WriteAbleStandardRadixTree<?> tree) throws Exception {
        Field rootField = WriteAbleStandardRadixTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        return dumpNode(rootField.get(tree));
    }

    private static String dumpNode(Object node) throws Exception {
        CharSequence edge = (CharSequence) field(node, "edge").get(node);
        Object value = field(node, "value").get(node);
        @SuppressWarnings("unchecked")
        Map<Character, Object> children = (Map<Character, Object>) field(node, "children").get(node);
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(edge).append('=').append(value);
        List<Character> firstChars = new ArrayList<>(children.keySet());
        firstChars.sort(Comparator.naturalOrder());
        for (Character firstChar : firstChars) {
            sb.append(' ').append(dumpNode(children.get(firstChar)));
        }
        return sb.append(']').toString();
    }

    private static Field field(Object node, String name) throws Exception {
        Field f = node.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
