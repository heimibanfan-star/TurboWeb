package org.heimi;

import org.junit.jupiter.api.Test;
import top.turboweb.commons.struct.trie.PatternUrlTrie;

import static org.junit.jupiter.api.Assertions.*;

class PatternUrlTrieTest {

    @Test
    void testExactMatch() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/a/b/c", "abc", false);
        trie.insert("/a/b/d", "abd", false);

        assertEquals("abc", trie.match("/a/b/c"));
        assertEquals("abd", trie.match("/a/b/d"));
        assertNull(trie.match("/a/b"));
        assertNull(trie.match("/a/b/c/d"));
    }

    @Test
    void testSingleStarMatch() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/user/*/profile", "userProfile", false);

        assertEquals("userProfile", trie.match("/user/123/profile"));
        assertEquals("userProfile", trie.match("/user/abc/profile"));
        assertNull(trie.match("/user/123/extra/profile"));
        assertNull(trie.match("/user/123"));
    }

    @Test
    void testDoubleStarMatch() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/files/**", "allFiles", false);

        assertEquals("allFiles", trie.match("/files/"));
        assertEquals("allFiles", trie.match("/files/a"));
        assertEquals("allFiles", trie.match("/files/a/b/c"));
        assertNull(trie.match("/file")); // 注意: /file 不匹配 /files/**
    }

    @Test
    void testStarAndDoubleStarTogether() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/data/*/images/**", "dataImages", false);

        assertEquals("dataImages", trie.match("/data/123/images/"));
        assertEquals("dataImages", trie.match("/data/123/images/a"));
        assertEquals("dataImages", trie.match("/data/123/images/a/b/c"));
        assertNull(trie.match("/data/123/files/a/b"));
    }

    @Test
    void testRootMatch() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/**", "allRoot", false);

        assertEquals("allRoot", trie.match("/"));
        assertEquals("allRoot", trie.match("/any/path"));
        assertEquals("allRoot", trie.match("/another/path/segment"));
    }

    @Test
    void testMultiplePatternsPriority() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        trie.insert("/files/**", "allFiles", false);
        trie.insert("/files/special/*", "specialFiles", false);

        // 精确/单星匹配优先于双星
        assertEquals("specialFiles", trie.match("/files/special/a"));
        assertEquals("allFiles", trie.match("/files/other/path"));
    }

    @Test
    void testInvalidPatternInsertion() {
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/a/**/b/**", "invalid", false));
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/a/**/b/*", "invalid", false));
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/a/*/**/**", "invalid", false));
    }
}
