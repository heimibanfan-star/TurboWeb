package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestUrlTrie} 插入期的路径参数校验矩阵（spec §5：struct.trie 全家族）。
 *
 * <p>原钉住 F1/F1b 的用例已随 2026-10-07 的修复改为期望正确行为（§10.7），其余用例断言既有语义。
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
    @DisplayName("F1（已修）：参数路径重复插入与静态路径一致地判重拒绝，size 不再虚增")
    void duplicateParameterPathIsRejectedLikeStaticPath() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "v1");
        // 修复口径：重复性判断挪到归一化后的键上——map 键实为 {id:str}，判断同样先归一化再查。
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> t.insert("/a/{id}", "v2"));
        assertEquals("key already exists", ex.getMessage());
        // "{id}" 与 "{id:str}" 归一化后同键——同一路由的两种拼写，第二条也应判重复。
        assertThrows(IllegalArgumentException.class, () -> t.insert("/a/{id:str}", "v3"));

        assertEquals("v1", t.get("/a/{id}"), "被拒绝的插入不得改动原值");
        assertEquals(1, t.size(), "被拒绝的插入不得计入 size");
        assertEquals(1, t.root.get("a").subKeys().size(), "map 仍只有 {id:str} 一个键");
    }

    @Test
    @DisplayName("F1 边界：overwrite=true 对参数路径替换值且 size 不增")
    void overwriteOnParameterPathReplacesValueWithoutGrowingSize() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "v1");
        t.insert("/a/{id}", "v2", true);
        assertEquals("v2", t.get("/a/{id}"));
        assertEquals(1, t.size(), "覆盖走既有节点，不得再记一个新节点");
    }

    @Test
    @DisplayName("F1b（已修）：原始占位符键可用精确 get 取回，与 match 行为一致")
    void rawPlaceholderKeyIsReachableByExactGet() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "v1");

        assertEquals("v1", t.get("/a/{id}"), "get 与 insert 同源，先归一化段再查 map");
        assertEquals("v1", t.get("/a/{id:str}"), "归一化键照常可取——回归：不得弄坏原存储键路径");
        assertTrue(t.exists("/a/{id}"), "exists 跟随 get");
        assertNull(t.get("/a/9"), "具体值不是精确键，只有 match 该命中它");
        assertEquals("v1", t.match("/a/9").value, "match 仍命中参数节点");
    }
}
