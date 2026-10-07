package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestUrlTrie} 插入期的路径参数校验矩阵（spec §5：struct.trie 全家族）。
 *
 * <p>这里断言的是既有语义，不是理想语义：钉住 F1/F1b 的用例在缺陷修正后应当改为期望正确行为。
 */
class RestUrlTrieInsertTest {

    private static RestUrlTrie<String> trie() {
        return new RestUrlTrie<>();
    }

    @Test
    void barePlaceholderIsNormalizedToTypedKey() {
        RestUrlTrie<String> t = trie();
        t.insert("/users/{id}", "handler");

        assertEquals(1, t.size());
        Set<String> subKeys = t.root.get("users").subKeys();
        assertTrue(subKeys.contains("{id:str}"), () -> "无类型占位符应归一化为 {id:str}，实际: " + subKeys);
        assertFalse(subKeys.contains("{id}"), "原始占位符不应作为 map 键保留");
    }

    @Test
    void typedPlaceholderKeepsItsOriginalKeyVerbatim() {
        RestUrlTrie<String> t = trie();
        // 归一化只在“无类型”时发生：带类型的键原样入 map，连大小写都不折叠
        t.insert("/users/{id:NUM}", "handler");

        assertTrue(t.root.get("users").subKeys().contains("{id:NUM}"),
                "带类型占位符应原样入 map（类型名查找才做小写折叠）");
    }

    @Test
    void everySupportedParamTypeIsAccepted() {
        String[] types = {"num", "int", "bool", "date", "ipv4", "path", "str"};
        for (String type : types) {
            RestUrlTrie<String> t = trie();
            t.insert("/x/{p:" + type + "}", "v");
            assertEquals(1, t.size(), () -> "类型 " + type + " 应当可插入");
        }
    }

    @Test
    void regexTypePrefixIsAccepted() {
        RestUrlTrie<String> t = trie();
        t.insert("/x/{p:regex=^a+$}", "v");
        assertEquals(1, t.size());
    }

    @Test
    void nullKeyIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert(null, "v"));
        assertEquals("Key cannot be null or empty", ex.getMessage());
    }

    @Test
    void emptyKeyIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("", "v"));
        assertEquals("Key cannot be null or empty", ex.getMessage());
    }

    @Test
    void emptySegmentIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a//b", "v"));
        assertTrue(ex.getMessage().startsWith("Empty path segment is not allowed:"), ex.getMessage());
    }

    @Test
    void doubleColonInPlaceholderIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a::int}", "v"));
        assertEquals("Invalid parameter syntax in: {a::int}", ex.getMessage());
    }

    @Test
    void trailingColonInPlaceholderIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a:}", "v"));
        assertEquals("Invalid parameter syntax in: {a:}", ex.getMessage());
    }

    @Test
    void duplicateParamNameIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a}/x/{a}", "v"));
        assertTrue(ex.getMessage().startsWith("Duplicate path variable 'a' in path:"), ex.getMessage());
    }

    @Test
    void duplicateParamNameIsRejectedEvenWithDifferentTypes() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a:int}/{a:str}", "v"));
        assertTrue(ex.getMessage().startsWith("Duplicate path variable 'a'"),
                "重名判定只看名字，与类型无关: " + ex.getMessage());
    }

    @Test
    void illegalParamNameIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{1a}", "v"));
        assertEquals("Invalid path variable name: 1a", ex.getMessage());
    }

    @Test
    void hyphenatedParamNameIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a-b}", "v"));
        assertEquals("Invalid path variable name: a-b", ex.getMessage());
    }

    @Test
    void unsupportedParamTypeIsRejected() {
        RestUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/{a:foo}", "v"));
        assertEquals("Unsupported parameter type 'foo' in path: /{a:foo}", ex.getMessage());
    }

    @Test
    void duplicateStaticPathIsRejectedWithoutOverwrite() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/b", "v1");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a/b", "v2"));
        assertEquals("key already exists", ex.getMessage());
        assertEquals("v1", t.get("/a/b"), "被拒绝的插入不得改动原值");
        assertEquals(1, t.size());
    }

    @Test
    void overwriteFlagReplacesStaticValueWithoutGrowingSize() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/b", "v1");
        t.insert("/a/b", "v2", true);
        assertEquals("v2", t.get("/a/b"));
        assertEquals(1, t.size());
    }

    @Test
    @DisplayName("钉住 F1：参数路径重复插入静默替换旧值且 size 虚增")
    void duplicateParameterPathSilentlyReplacesAndInflatesSize() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "v1");
        // 期望的正确行为是抛 "key already exists"（与静态路径一致）；当前不抛。
        // 钉住 F1：若将来修正，本用例应改为期望正确行为（修正 F1 后，本用例应改为 assertThrows）。
        assertDoesNotThrow(() -> t.insert("/a/{id}", "v2"));

        assertEquals(2, t.size(), "同一路径两次插入被记成 2 个节点——size 记账被虚增");
        assertEquals(1, t.root.get("a").subKeys().size(), "map 里实际只有一个键 {id:str}");
    }

    @Test
    @DisplayName("钉住 F1b：原始占位符键无法用精确 get 取回")
    void rawPlaceholderKeyIsNotReachableByExactGet() {
        // 钉住 F1b：若将来修正，本用例应改为期望正确行为
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "v1");

        assertNull(t.get("/a/{id}"), "get 用未归一化的段查 map，必然落空");
        assertEquals("v1", t.match("/a/9").value, "match 走归一化后的参数节点，正常命中");
    }
}
