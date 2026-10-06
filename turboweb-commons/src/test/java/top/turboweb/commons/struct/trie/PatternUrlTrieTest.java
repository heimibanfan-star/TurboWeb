package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PatternUrlTrie} 的 {@code *}（单段）与 {@code **}（零到多段）语义。
 */
class PatternUrlTrieTest {

    private static PatternUrlTrie<String> trie() {
        return new PatternUrlTrie<>();
    }

    @Test
    void staticKeyMatchesItselfOnly() {
        PatternUrlTrie<String> t = trie();
        t.insert("/a/b", "v");
        assertEquals(Set.of("v"), t.match("/a/b"));
        assertTrue(t.match("/a/c").isEmpty(), "未命中返回空集而不是 null");
    }

    @Test
    void singleStarMatchesExactlyOneSegment() {
        PatternUrlTrie<String> t = trie();
        t.insert("/user/*", "v");
        assertEquals(Set.of("v"), t.match("/user/123"));
        assertTrue(t.match("/user/abc").size() == 1);
        assertTrue(t.match("/user/a/b").isEmpty(), "* 只吃一段");
        // "必须有剩余段"的真正守门人是 dfs :107 的收集判据 index == segs.length：
        // 把 dfs :117 的 if (index < segs.length) 改成恒真，本例不会变红——越界递归
        // 永远凑不上收集条件，那条守卫是冗余的（变异取证实测等价）。
        assertTrue(t.match("/user").isEmpty(), "* 必须有剩余段");
    }

    @Test
    void doubleStarMatchesZeroOrMoreSegments() {
        PatternUrlTrie<String> t = trie();
        t.insert("/files/**", "v");
        assertEquals(Set.of("v"), t.match("/files"), "** 可以吃零段");
        assertEquals(Set.of("v"), t.match("/files/a"));
        assertEquals(Set.of("v"), t.match("/files/a/b/c"));
    }

    @Test
    void doubleStarInMiddleWorks() {
        PatternUrlTrie<String> t = trie();
        t.insert("/**/x", "v");
        assertEquals(Set.of("v"), t.match("/a/b/x"));
        assertEquals(Set.of("v"), t.match("/x"));
    }

    @Test
    void overlappingPatternsAllCollectTheirValues() {
        PatternUrlTrie<String> t = trie();
        t.insert("/x/*", "V-single");
        t.insert("/x/y", "V-exact");
        t.insert("/x/**", "V-multi");
        assertEquals(Set.of("V-single", "V-exact", "V-multi"), t.match("/x/y"),
                "dfs 收集所有可行路径，不做优先级裁剪");
    }

    @Test
    void invalidKeyIsRejected() {
        PatternUrlTrie<String> t = trie();
        // 连字符不在 [A-Za-z0-9_] 里
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a-b", "v"));
        assertEquals("key is invalid: /a-b", ex.getMessage());
    }

    @Test
    void keyWithoutLeadingSlashIsRejected() {
        PatternUrlTrie<String> t = trie();
        assertThrows(IllegalArgumentException.class, () -> t.insert("a/b", "v"));
    }

    @Test
    void emptySegmentMakesKeyInvalid() {
        PatternUrlTrie<String> t = trie();
        // VALID_KEY 要求每段非空，"//x" 落不进去。断言精确消息：只断言异常类型分不出是谁抛的——
        // 即便 VALID_KEY 形同虚设，"//x" 也会在 AbstractTrie.splitKey 之后的空段校验里抛
        // IllegalArgumentException("subKey cannot be empty")。
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("//x", "v"));
        assertEquals("key is invalid: //x", ex.getMessage());
    }

    @Test
    void doubleStarTwiceIsRejected() {
        PatternUrlTrie<String> t = trie();
        // 必须断言消息前缀：把 :60 的重复 '**' 判定关掉，插入照样抛 IAE——
        // 后面那条"** 之后不能再跟通配符"是它的超集（第二个 ** 里含 *），只是换了消息。
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/**/a/**", "v"));
        assertTrue(ex.getMessage().startsWith("'**' can only appear once:"), ex.getMessage());
    }

    @Test
    void doubleStarFollowedByStarIsRejected() {
        PatternUrlTrie<String> t = trie();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/**/*", "v"));
        assertTrue(ex.getMessage().startsWith("'**' cannot be followed by '*' or '**':"), ex.getMessage());
    }

    @Test
    void duplicateStaticInsertIsRejectedUnlikeParameterPaths() {
        PatternUrlTrie<String> t = trie();
        t.insert("/a/b", "v1");
        // handleSubKey 返回 null → 不做归一化 → AbstractTrie 的重复判定在这里是有效的
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> t.insert("/a/b", "v2"));
        assertEquals("key already exists", ex.getMessage());
        t.insert("/a/b", "v2", true);
        assertEquals(Set.of("v2"), t.match("/a/b"));
    }

    @Test
    void matchWithoutLeadingSlashReturnsNull() {
        PatternUrlTrie<String> t = trie();
        t.insert("/a/b", "v");
        // UrlTrie.match 的入参守卫发生在 doMatch 之前，所以是 null 而不是空集
        assertTrue(t.match("a/b") == null, "无效输入返回 null，未命中返回空集——两种语义并存");
    }

    @Test
    @DisplayName("钉住 F15：null 键在 PatternUrlTrie 里抛 NPE 而非 IAE")
    void nullKeyThrowsNpeNotIae() {
        PatternUrlTrie<String> t = trie();
        // RestUrlTrie 对 null 抛 IAE("Key cannot be null or empty")，本类直接把它喂给 matcher()
        assertThrows(NullPointerException.class, () -> t.insert(null, "v"));
    }
}
