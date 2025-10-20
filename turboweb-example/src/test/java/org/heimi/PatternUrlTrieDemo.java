package org.heimi;

import top.turboweb.commons.struct.trie.PatternUrlTrie;

public class PatternUrlTrieDemo {
    public static void main(String[] args) {
        System.out.println("=== PatternUrlTrie Demo ===");
        
        // 创建PatternUrlTrie实例
        PatternUrlTrie<String> trie = new PatternUrlTrie<>();
        
        // 插入测试数据
        System.out.println("\n1. Inserting test patterns...");
        trie.insert("/a/b/c", "Exact match: /a/b/c", true);
        trie.insert("/a/*/c", "Single star: /a/*/c", true);
        trie.insert("/a/**/d", "Double star: /a/**/d", true);
        trie.insert("/**/x/y", "Double star start: /**/x/y", true);
        trie.insert("/*/b/*", "Multiple stars: /*/b/*", true);
        trie.insert("/**", "Match all: /**", true);
        
        // 测试匹配
        System.out.println("\n2. Testing matches...");
        
        // 精确匹配测试
        testMatch(trie, "/a/b/c", "Exact match test");
        
        // * 匹配测试
        testMatch(trie, "/a/x/c", "Single star match test 1");
        testMatch(trie, "/a/y/c", "Single star match test 2");
        testMatch(trie, "/a/x/y/c", "Single star no match test");
        
        // ** 匹配测试
        testMatch(trie, "/a/d", "Double star match test 1 (0 levels)");
        testMatch(trie, "/a/x/d", "Double star match test 2 (1 level)");
        testMatch(trie, "/a/x/y/z/d", "Double star match test 3 (multiple levels)");
        testMatch(trie, "/x/y", "Double star start test 1");
        testMatch(trie, "/a/b/x/y", "Double star start test 2");
        
        // 多通配符测试
        testMatch(trie, "/x/b/y", "Multiple stars test");
        
        // 最佳匹配测试
        System.out.println("\n3. Best match test...");
        trie.insert("/a/b/*", "More specific: /a/b/*", true);
        testMatch(trie, "/a/b/d", "Best match test");
        
        // 匹配所有测试
        testMatch(trie, "/any/path", "Match all test 1");
        testMatch(trie, "/", "Match all test 2");
        
        // 不匹配测试
        testMatch(trie, "/none/existent/path", "No match test");
        
        // 测试无效键
        System.out.println("\n4. Invalid key test...");
        try {
            trie.insert("/a/b?c", "Invalid key", true);
            System.out.println("ERROR: Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.out.println("SUCCESS: Caught exception for invalid key: " + e.getMessage());
        }
        
        // 测试多个**
        try {
            trie.insert("/a/**/b/**/c", "Multiple **", true);
            System.out.println("ERROR: Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.out.println("SUCCESS: Got exception for multiple **: " + e.getMessage());
        }
        
        System.out.println("\n=== Demo completed ===");
    }
    
    private static void testMatch(PatternUrlTrie<String> trie, String path, String testName) {
        System.out.printf("%-40s: %s%n", testName, path);
        String result = trie.match(path);
        if (result != null) {
            System.out.printf("%-40s  -> %s%n", "", result);
        } else {
            System.out.printf("%-40s  -> No match%n", "");
        }
    }
}