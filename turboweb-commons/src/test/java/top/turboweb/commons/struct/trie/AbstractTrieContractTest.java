package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractTrie} 骨架契约（spec §5：struct.trie 全家族）。
 *
 * <p>钉住现状的用例都带 DisplayName 说明缺陷编号，修正对应缺陷时这些用例应当改为期望正确行为。
 */
class AbstractTrieContractTest {

    @Test
    void insertThenGetReturnsValue() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        assertEquals("v", t.get("/a/b"));
        assertTrue(t.exists("/a/b"));
    }

    @Test
    void sizeCountsValueNodesOnly() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        assertEquals(1, t.size(), "中间节点 a 无值，不得计入 size");
    }

    @Test
    void emptySegmentIsRejectedByBaseClass() {
        SlashTrie t = new SlashTrie();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> t.insert("//a", "v"));
        assertEquals("subKey cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("钉住 F20：尾部空段被 String.split 丢弃，\"/a//\" 被当成 \"/a\" 静默接受")
    void trailingEmptySegmentsAreSilentlyDropped() {
        // 钉住 F20：若将来修正，本用例应改为期望正确行为
        SlashTrie t = new SlashTrie();
        // splitKey 裁掉首尾各一个分隔符："/a//" -> "a/"，而 "a/".split("/") -> ["a"]
        // 尾空段不会进循环，所以这里不抛 IAE，而是等价插入 "/a"。中间的 "" 仍会抛（见上一条用例）。
        t.insert("/a//", "v");

        assertEquals("v", t.get("/a"), "被静默折叠成 /a");
        assertEquals(1, t.size());
        assertEquals("v", t.get("/a//"), "读写走同一套裁剪，尾部斜杠形式也能读回——缺陷在于它绕过了重复检测");
        assertThrows(IllegalArgumentException.class, () -> t.insert("/a", "dup"),
                "折叠成同一节点后，真正的 /a 会被判重复；反过来 /a// 也应当被判重复，当前不会");
    }

    @Test
    void nullKeyIsRejectedBySplitKey() {
        SlashTrie t = new SlashTrie();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> t.insert(null, "v"));
        assertEquals("key cannot be null or empty", ex.getMessage(),
                "基类文案与 RestUrlTrie 的“Key cannot be null or empty”大小写不同");
    }

    @Test
    void blankSplitStringIsRejected() {
        AbstractTrie<String, String> t = new AbstractTrie<>() {
            @Override
            protected Details handleSubKey(String subKey) {
                return null;
            }

            @Override
            protected String splitString() {
                return "";
            }

            @Override
            public String match(String key) {
                return null;
            }
        };
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> t.insert("/a", "v"));
        assertEquals("splitString cannot be null or empty", ex.getMessage());
    }

    @Test
    void duplicateInsertIsRejectedAndKeepsOriginalValue() {
        SlashTrie t = new SlashTrie();
        t.insert("/a", "v1");
        assertThrows(IllegalArgumentException.class, () -> t.insert("/a", "v2"));
        assertEquals("v1", t.get("/a"));
    }

    @Test
    void overwriteReplacesValueWithoutGrowingSize() {
        SlashTrie t = new SlashTrie();
        t.insert("/a", "v1");
        t.insert("/a", "v2", true);
        assertEquals("v2", t.get("/a"));
        assertEquals(1, t.size());
    }

    @Test
    void getOnEmptyTrieReturnsNull() {
        SlashTrie t = new SlashTrie();
        assertNull(t.get("/a"), "size==0 短路");
    }

    @Test
    void getMissingBranchReturnsNull() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        assertNull(t.get("/a/c"));
        assertNull(t.get("/zz"));
        assertNull(t.get("/a/b/c"), "更长的路径落到不存在的子节点");
    }

    @Test
    void rootsExposesTopLevelSegments() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v1");
        t.insert("/c", "v2");
        assertEquals(Set.of("a", "c"), t.roots());
    }

    @Test
    void deleteClearsValueAndPrunesEmptyBranch() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        t.delete("/a/b");
        assertEquals(0, t.size());
        assertNull(t.get("/a/b"));
        assertTrue(t.root.subKeys().isEmpty(), "空分支应被逐级剪掉");
    }

    @Test
    void deleteKeepsSharedPrefixAlive() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v1");
        t.insert("/a/c", "v2");
        t.delete("/a/b");
        assertEquals("v2", t.get("/a/c"));
        assertEquals(1, t.size());
        assertTrue(t.root.subKeys().contains("a"), "仍有兄弟节点，前缀 a 不得被剪");
    }

    @Test
    void deleteOfMissingKeyIsNoOp() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        t.delete("/zz");
        assertEquals(1, t.size());
        assertEquals("v", t.get("/a/b"));
    }

    @Test
    void deleteOfValuelessIntermediateNodeIsNoOp() {
        SlashTrie t = new SlashTrie();
        t.insert("/a/b", "v");
        t.delete("/a");
        assertEquals(1, t.size(), "中间节点无值，删除请求应当被忽略");
        assertEquals("v", t.get("/a/b"));
    }

    @Test
    void deleteOnEmptyTrieIsNoOp() {
        SlashTrie t = new SlashTrie();
        t.delete("/a");
        assertEquals(0, t.size());
    }

    @Test
    @DisplayName("F6 探针：split 永不返回零长度数组，故三处 strs.length==0 分支不可达")
    void splitNeverProducesZeroLengthArray() {
        SlashTrie t = new SlashTrie();
        // splitKey 会先裁掉首尾各一个分隔符，"/" 变成 ""，而 "".split("/") == [""]
        assertArrayEqualsCompat(new String[]{""}, t.splitKey("/"));
        assertArrayEqualsCompat(new String[]{"a"}, t.splitKey("/a/"));
    }

    /** 局部小助手：避免为一个断言引入 AssertJ（spec §2.2 未批准新增测试库）。 */
    private static void assertArrayEqualsCompat(String[] expected, String[] actual) {
        assertEquals(List.of(expected), List.of(actual));
    }
}
