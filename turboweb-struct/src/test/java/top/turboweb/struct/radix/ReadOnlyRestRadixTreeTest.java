package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReadOnlyRestRadixTree} 活视图：同实例缓存、match 委托（含 null 校验同源）、
 * 后续 put / remove 对视图可见。
 */
class ReadOnlyRestRadixTreeTest {

    @Test
    void toReadViewReturnsSameInstanceOnRepeatedCalls() {
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        tree.put("/a", 1);
        RestRadixTree<Integer> view = tree.toReadView();
        assertSame(view, tree.toReadView());
    }

    @Test
    void readViewDelegatesMatchWithValueAndParams() {
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        tree.put("/user/:id", 1);
        RestRadixTree<Integer> view = tree.toReadView();
        RestRadixTree.MatchResult<Integer> hit = view.match("/user/42");
        assertEquals(1, hit.value);
        assertEquals(Map.of("id", "42"), hit.params);
    }

    @Test
    void readViewSeesSubsequentPutAndRemove() {
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        tree.put("/a", 1);
        RestRadixTree<Integer> view = tree.toReadView();
        assertNull(tree.put("/b", 2));
        assertEquals(2, view.match("/b").value, "建视图后的 put 对视图可见");
        assertTrue(tree.remove("/a"));
        assertNull(view.match("/a"), "建视图后的 remove 对视图可见");
    }

    @Test
    void readViewBacktracksAndNormalizesLikeWriteSide() {
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RestRadixTree<Integer> view = tree.toReadView();
        assertEquals(1, view.match("//user//admin/").value, "归一化与字面优先同写侧");
        assertEquals(2, view.match("/user/posts").value, "回溯命中参数分支同写侧");
    }

    @Test
    void readViewRejectsNullPathLikeWriteSide() {
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        RestRadixTree<Integer> view = tree.toReadView();
        assertEquals("The path argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.match(null)).getMessage());
    }
}
