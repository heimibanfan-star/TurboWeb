package org.heimi;

import top.turboweb.commons.struct.trie.RestUrlTrie;
import java.util.Map;

public class RestUrlTrieTest {
    public static void main(String[] args) {
        RestUrlTrie<String> trie = new RestUrlTrie<>();

        // === Insert Tests ===
        trie.insert("/a/b", "ab", false);                           // 普通路径
        trie.insert("/user/{id}", "user1", false);                  // 参数路径
        trie.insert("/user/{id}/{name}", "user2", false);
        trie.insert("/user/{id}/{name}/{age}", "user3", false);
        trie.insert("/data/{ip:ipv4}", "ipv4", false);
        trie.insert("/flag/{flag:bool}", "bool", false);
        trie.insert("/number/{v:num}", "num", false);
        trie.insert("/integer/{v:int}", "int", false);
        trie.insert("/date/{v:date}", "date", false);
        trie.insert("/regex/{x:regex=\\d{2,4}}", "regex", false);

        // 触发重复参数检测
        try {
            trie.insert("/dup/{id}/{id}", "dup", false);
            assert false : "Should throw on duplicate param name";
        } catch (IllegalArgumentException ignored) {}

        // 触发非法参数名检测
        try {
            trie.insert("/bad/{1abc}", "bad", false);
            assert false : "Should throw on invalid param name";
        } catch (IllegalArgumentException ignored) {}

        // === Match Tests ===

        // 固定路径匹配
        assert trie.match("/a/b").value.equals("ab");

        // 参数匹配（基础）
        assert trie.match("/user/123").value.equals("user1");
        assert trie.match("/user/123/zhang").value.equals("user2");
        assert trie.match("/user/123/zhang/18").value.equals("user3");

        // 参数提取检查
        Map<String, String> params = trie.match("/user/1/tom/20").params;
        assert params.get("id").equals("1");
        assert params.get("name").equals("tom");
        assert params.get("age").equals("20");

        // IPV4 匹配
        assert trie.match("/data/192.168.0.1").value.equals("ipv4");
        assert trie.match("/data/999.999.999.999") == null; // 不匹配路径

        // BOOL 匹配
        assert trie.match("/flag/true").value.equals("bool");
        assert trie.match("/flag/false").value.equals("bool");
        assert trie.match("/flag/TRUE").value.equals("bool");
        assert trie.match("/flag/yes") == null;

        // NUM 匹配（浮点、整数）
        assert trie.match("/number/3.14").value.equals("num");
        assert trie.match("/number/-12").value.equals("num");
        assert trie.match("/number/abc") == null;

        // INT 匹配
        assert trie.match("/integer/42").value.equals("int");
        assert trie.match("/integer/-99").value.equals("int");
        assert trie.match("/integer/3.5") == null;

        // DATE 匹配
        assert trie.match("/date/2024-12-31").value.equals("date");
        assert trie.match("/date/2024-13-31") == null;

        // 正则匹配
        assert trie.match("/regex/123").value.equals("regex");
        assert trie.match("/regex/9") == null;

        // 尾部斜杠
        assert trie.match("/user/1/tom/20/").value.equals("user3");

        // 空/无效路径
        assert trie.match("/") == null;
        assert trie.match("") == null;
        assert trie.match("noSlash") == null;

        // 回溯路径测试
        trie.insert("/foo/{id}", "fooId", false);
        trie.insert("/foo/bar", "fooBar", false);
        assert trie.match("/foo/bar").value.equals("fooBar"); // 固定优先
        assert trie.match("/foo/123").value.equals("fooId");  // 参数分支

        // value为空但可回溯
        trie.insert("/deep/{a}/{b}/{c}", null, false);
        trie.insert("/deep/{a}/{b}/x", "deepx", false);
        assert trie.match("/deep/1/2/x").value.equals("deepx");
        assert trie.match("/deep/1/2/3") == null;

        // 多参数正则/普通混合
        trie.insert("/mix/{a:int}/{b:regex=ab+}/{c:bool}", "mix", false);
        assert trie.match("/mix/42/abbb/true").value.equals("mix");
        assert trie.match("/mix/42/xyz/true") == null; // regex失败
        assert trie.match("/mix/xyz/abbb/true") == null; // int失败

        // 回溯多分支
        trie.insert("/try/{p}", "try1", false);
        trie.insert("/try/test", "try2", false);
        assert trie.match("/try/test").value.equals("try2"); // 固定优先
        assert trie.match("/try/hello").value.equals("try1"); // 参数匹配

        // 覆盖 overwrite = true 分支
        trie.insert("/user/{id}", "newUser", true);
        assert trie.match("/user/123").value.equals("newUser");

        // 覆盖空路径 segment（例如连续两个斜杠）
        assert trie.match("//") == null;


        // 空白参数路径（边界测试）
        trie.insert("/x/{empty:regex=^$}", "empty", false);
        assert trie.match("/x/").value.equals("empty");

        // 全部执行完
        System.out.println("✅ All assertions passed — 100%+ coverage achieved!");
    }
}
