package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PatternPathTrie} 的插入冲突、参数匹配、通配符与删除语义。
 *
 * <p>本类已 {@code @Deprecated} 且生产侧零调用者，仍写满用例是 spec §2.1
 * 冻结清单的代价：用例负责把废弃语义钉死，将来删除时能知道自己放弃了什么。
 */
class PatternPathTrieTest {

    private static PatternPathTrie<String> trie() {
        return new PatternPathTrie<>();
    }

    @Test
    void staticPathRoundTrips() {
        PatternPathTrie<String> t = trie();
        t.insert("/users/me", "v");
        Optional<PathTrie.MatchResult<String>> hit = t.paramMatch("/users/me");
        assertTrue(hit.isPresent());
        assertEquals("v", hit.get().getValue());
        assertTrue(hit.get().getParams().isEmpty());
        assertTrue(t.containsExact("/users/me"));
    }

    @Test
    void paramExtractsTypedValue() {
        PatternPathTrie<String> t = trie();
        t.insert("/u/{id:int}", "v");
        Optional<PathTrie.MatchResult<String>> hit = t.paramMatch("/u/42");
        assertTrue(hit.isPresent());
        assertEquals("42", hit.get().getParams().get("id"));
        assertFalse(t.paramMatch("/u/abc").isPresent(), "int 类型过滤");
    }

    @Test
    void bareParamDefaultsToStr() {
        PatternPathTrie<String> t = trie();
        t.insert("/u/{id}", "v");
        assertTrue(t.paramMatch("/u/anything").isPresent());
    }

    @Test
    void pathParamTypeIsUnsupportedHere() {
        PatternPathTrie<String> t = trie();
        // 本类自带的 ParamType 没有 PATH 常量（与 RestUrlTrie 不同）
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/f/{p:path}", "v"));
        assertTrue(ex.getMessage().startsWith("Unsupported param type:"), ex.getMessage());
    }

    @Test
    void regexParamWorksHereToo() {
        PatternPathTrie<String> t = trie();
        t.insert("/x/{p:regex=^[a-c]+$}", "v");
        assertTrue(t.paramMatch("/x/abc").isPresent());
        assertFalse(t.paramMatch("/x/xyz").isPresent());
    }

    @Test
    void duplicatePathIsRejected() {
        PatternPathTrie<String> t = trie();
        t.insert("/a/b", "v1");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a/b", "v2"));
        assertTrue(ex.getMessage().startsWith("路径已存在，无法重复插入:"), ex.getMessage());
    }

    @Test
    void sameLayerSameTypeDifferentNameConflicts() {
        PatternPathTrie<String> t = trie();
        t.insert("/u/{a:str}", "v1");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/u/{b:str}", "v2"));
        assertTrue(ex.getMessage().startsWith("路径参数冲突"), ex.getMessage());
    }

    @Test
    void sameLayerDifferentTypeIsAllowed() {
        PatternPathTrie<String> t = trie();
        t.insert("/u/{a:int}", "v1");
        t.insert("/u/{b:date}", "v2");
        assertEquals("v1", t.paramMatch("/u/1").orElseThrow().getValue());
        assertEquals("v2", t.paramMatch("/u/2026-10-06").orElseThrow().getValue());
    }

    @Test
    void paramMatchRejectsNullAndMissingLeadingSlash() {
        PatternPathTrie<String> t = trie();
        t.insert("/a", "v");
        IllegalArgumentException nullEx =
                assertThrows(IllegalArgumentException.class, () -> t.paramMatch(null));
        assertTrue(nullEx.getMessage().startsWith("路径不能为空且必须以'/'开头"), nullEx.getMessage());
        assertThrows(IllegalArgumentException.class, () -> t.paramMatch("a"));
    }

    @Test
    void singleStarMatchesExactlyOneSegment() {
        PatternPathTrie<String> t = trie();
        t.insert("/a/*", "v");
        assertTrue(t.patternMatch("/a/b").contains("v"));
        assertTrue(t.patternMatch("/a/b/c").isEmpty());
    }

    @Test
    void doubleStarMatchesZeroOrMoreSegments() {
        PatternPathTrie<String> t = trie();
        t.insert("/a/**", "v");
        assertTrue(t.patternMatch("/a").contains("v"), "** 零层特判：idx == len 时直接取 multiNode");
        assertTrue(t.patternMatch("/a/b/c").contains("v"));
    }

    @Test
    void patternMatchRejectsBadInput() {
        PatternPathTrie<String> t = trie();
        t.insert("/a", "v");
        assertThrows(IllegalArgumentException.class, () -> t.patternMatch(null));
        assertThrows(IllegalArgumentException.class, () -> t.patternMatch("a"));
    }

    @Test
    void tooManyDoubleStarsRejectedAtInsert() {
        PatternPathTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a/**/b/**", "v"));
        assertTrue(ex.getMessage().startsWith("路径中**出现的次数不能超过1"), ex.getMessage());
    }

    @Test
    void prefixLookupsValidateTheirArgument() {
        PatternPathTrie<String> t = trie();
        t.insert("/a/b", "v");
        assertEquals("前缀不能为空",
                assertThrows(IllegalArgumentException.class, () -> t.getEntriesWithPrefix(null)).getMessage());
        assertEquals("前缀必须为空或以 '/' 开头",
                assertThrows(IllegalArgumentException.class, () -> t.getEntriesWithPrefix("a")).getMessage());
        assertTrue(t.getEntriesWithPrefix("/zz").isEmpty(), "前缀不存在时返回空 map");
    }

    @Test
    void getEntriesWithPrefixCollectsSubtree() {
        PatternPathTrie<String> t = trie();
        t.insert("/a/b", "v1");
        t.insert("/a/c/d", "v2");
        t.insert("/x", "v3");
        Map<String, String> entries = t.getEntriesWithPrefix("/a");
        assertEquals(Set.of("v1", "v2"), Set.copyOf(entries.values()));
        assertEquals(Set.of("/a/b", "/a/c/d"), entries.keySet());
    }

    @Test
    @DisplayName("钉住 F4：allPaths 暴露归一化占位键而非插入时的原始路径")
    void allPathsExposesNormalizedKeys() {
        PatternPathTrie<String> t = trie();
        t.insert("/x/{p}", "v");
        // 钉住 F4：若将来修正，本用例应改为期望正确行为（修正 F4 后本用例应改为期望 "/x/{p}"）。
        // 实测比"只是补全类型"更严重：collectEntries 拼的是 children map 的键（:314 e.getKey()），
        // 而参数段的键由 keyForSegment 写成字面量 "{param:类型}"（:389），参数名在插入时就丢了——
        // 拿回来的既不是 "/x/{p}"，也不是 insert 内部归一化出的 "/x/{p:str}"，而是 "/x/{param:str}"。
        List<String> paths = t.allPaths();
        assertEquals(List.of("/x/{param:str}"), paths, "参数名被占位键 param 取代，原始写法彻底丢失");
    }

    @Test
    @DisplayName("钉住 F3：delete 返回的是整树是否剪空，不是该路径是否被删")
    void deleteReportsTreeEmptinessNotPathRemoval() {
        // 钉住 F3：若将来修正，本用例应改为期望正确行为
        PatternPathTrie<String> t = trie();
        t.insert("/a/b", "v1");
        t.insert("/a/c", "v2");

        assertFalse(t.delete("/a/b"), "路径确实被删了，但树没空 → 返回 false");
        assertEquals(List.of("/a/c"), t.allPaths());

        assertTrue(t.delete("/a/c"), "剪到 root 才返回 true");
        assertTrue(t.allPaths().isEmpty());
    }

    @Test
    void deleteOfMissingPathReturnsFalse() {
        PatternPathTrie<String> t = trie();
        t.insert("/a", "v");
        assertFalse(t.delete("/zz"));
        assertEquals(List.of("/a"), t.allPaths());
    }

    @Test
    void freshTrieHasNoPaths() {
        assertTrue(trie().allPaths().isEmpty());
    }

    @Test
    void pathTrieMatchResultIsPubliclyConstructible() {
        // 与 RestUrlTrie.MatchResult 相反：构造器 public、getParams() 直接返回原 map（不做 unmodifiable 包装）
        PathTrie.MatchResult<String> r = new PathTrie.MatchResult<>("v", Map.of("k", "1"));
        assertNotNull(r);
        assertEquals("v", r.getValue());
        assertEquals(Map.of("k", "1"), r.getParams());
    }
}
