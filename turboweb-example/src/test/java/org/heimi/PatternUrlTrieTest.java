package org.heimi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.struct.trie.PatternUrlTrie;

import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PatternUrlTrieTest {

    private PatternUrlTrie<String> trie;

    @BeforeEach
    void setUp() {
        trie = new PatternUrlTrie<>();
    }

    // 1. 静态路径匹配测试
    @Test
    void testStaticPathMatch() {
        trie.insert("/a/b/c", "static", false);
        // 完全匹配
        Set<String> result1 = trie.match("/a/b/c");
        assertEquals(Set.of("static"), result1);
        // 部分匹配（不完整）
        Set<String> result2 = trie.match("/a/b");
        assertTrue(result2.isEmpty());
        // 不匹配（路径不同）
        Set<String> result3 = trie.match("/a/d/c");
        assertTrue(result3.isEmpty());
    }

    // 2. 单级通配符*匹配测试
    @Test
    void testSingleWildcardMatch() {
        trie.insert("/a/*/c", "singleStar", false);
        // 正常匹配
        Set<String> result1 = trie.match("/a/b/c");
        assertEquals(Set.of("singleStar"), result1);
        // *匹配特殊字符段
        trie.insert("/x/*/z", "special", false);
        Set<String> result2 = trie.match("/x/123!@#/z");
        assertEquals(Set.of("special"), result2);
        // 段数不足（无法匹配*）
        Set<String> result3 = trie.match("/a/c");
        assertTrue(result3.isEmpty());
    }

    // 3. 多级通配符**匹配测试（0段、1段、多段）
    @Test
    void testMultiWildcardMatch() {
        // **匹配0段
        trie.insert("/a/**/d", "multi0", false);
        Set<String> result1 = trie.match("/a/d");
        assertEquals(Set.of("multi0"), result1);

        // **匹配1段
        Set<String> result2 = trie.match("/a/b/d");
        assertEquals(Set.of("multi0"), result2);

        // **匹配多段
        Set<String> result3 = trie.match("/a/b/c/d");
        assertEquals(Set.of("multi0"), result3);

        // **单独存在（匹配所有路径）
        trie.insert("/**", "all", false);
        Set<String> result4 = trie.match("/"); // 空路径
        assertEquals(Set.of("all"), result4);
        // 修正：/a/b/c/d 同时匹配**和/a/**/d
        Set<String> result5 = trie.match("/a/b/c/d");
        assertEquals(Set.of("all", "multi0"), result5);
    }

    @Test
    void testMixedWildcardsMatch() {
        // 合法路径：*在前，**在后（**之后无通配符）
        trie.insert("/a/*/b/**", "mix1", false);
        // 合法路径：**在前，静态段在后（**之后无任何通配符）
        trie.insert("/a/**/b/c", "mix2", false);

        // 匹配mix1：*匹配x，**匹配c/d
        // 匹配mix2：**匹配x/b，静态段c匹配最后一段
        Set<String> result1 = trie.match("/a/x/b/c/d");
        assertTrue(result1.contains("mix1")); // mix2不匹配（最后一段是d而非c）
        assertEquals(1, result1.size());

        // 匹配mix2：**匹配x，静态段b/c匹配后续
        Set<String> result2 = trie.match("/a/x/b/c");
        assertTrue(result2.containsAll(Set.of("mix1", "mix2"))); // mix1的**匹配0段，mix2完全匹配
        assertEquals(2, result2.size());

        // 仅匹配mix1（*匹配x，**匹配0段）
        Set<String> result3 = trie.match("/a/x/b");
        assertEquals(Set.of("mix1"), result3);
    }

    // 5. 多路径匹配冲突测试
    @Test
    void testMultipleMatches() {
        trie.insert("/a/*", "star", false);
        trie.insert("/a/**", "multi", false);

        // 同时匹配两个路径
        Set<String> result = trie.match("/a/b");
        assertEquals(Set.of("star", "multi"), result);
    }

    // 6. 边界条件测试（空路径、单段路径）
    @Test
    void testEdgeCases() {
        // 空路径（仅**匹配）
        trie.insert("/**", "empty", false);
        Set<String> result1 = trie.match("/");
        assertEquals(Set.of("empty"), result1);

        // 单段路径匹配
        trie.insert("/*", "singleSeg", false);
        Set<String> result2 = trie.match("/test");
        assertEquals(Set.of("empty", "singleSeg"), result2);
    }

    // 7. 不匹配场景测试
    @Test
    void testNoMatch() {
        trie.insert("/a/b/c", "val", false);
        trie.insert("/x/*/z", "val2", false);

        // 路径不匹配
        Set<String> result1 = trie.match("/a/c/b");
        assertTrue(result1.isEmpty());

        // 段数不匹配
        Set<String> result2 = trie.match("/x/y");
        assertTrue(result2.isEmpty());
    }

    // 8. 插入非法键测试（验证构造函数的校验逻辑）
    @Test
    void testInvalidInsert() {
        // 非法格式（不含/）
        assertThrows(IllegalArgumentException.class, () -> trie.insert("a/b/c", "val", false));

        // 多个**
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/a/**/b/**", "val", false));

        // **后有*
        assertThrows(IllegalArgumentException.class, () -> trie.insert("/**/*", "val", false));
    }
}