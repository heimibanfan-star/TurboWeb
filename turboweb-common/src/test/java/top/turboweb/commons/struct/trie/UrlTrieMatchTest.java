package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UrlTrie#match(String)} 的前导斜杠与拆分语义。
 */
class UrlTrieMatchTest {

    @Test
    void nullKeyMatchesNothing() {
        ExactUrlTrie t = new ExactUrlTrie();
        t.insert("/a/b", "v");
        assertNull(t.match(null));
    }

    @Test
    void keyWithoutLeadingSlashMatchesNothing() {
        ExactUrlTrie t = new ExactUrlTrie();
        t.insert("/a/b", "v");
        assertNull(t.match("a/b"), "URL 语义要求前导斜杠");
    }

    @Test
    void rootSlashYieldsEmptySegmentArray() {
        ExactUrlTrie t = new ExactUrlTrie();
        t.insert("/a", "v");
        // match("/") -> segs.length == 0 -> 夹具直接返回 null，不落到 get("")
        assertNull(t.match("/"));
    }

    @Test
    void redundantSlashesAreStrippedFromBothEnds() {
        ExactUrlTrie t = new ExactUrlTrie();
        t.insert("/a/b", "v");
        assertEquals("v", t.match("///a/b///"), "match 用 while 循环剥掉所有首尾斜杠");
    }

    @Test
    void innerEmptySegmentIsNotStripped() {
        ExactUrlTrie t = new ExactUrlTrie();
        t.insert("/a/b", "v");
        assertNull(t.match("/a//b"), "中间的空段不会被裁掉，落到不存在的节点");
    }
}
