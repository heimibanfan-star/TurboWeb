package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestRadixTree} 只读视图 ReadView：同一实例缓存、matchAny 委托（含 null 校验同源）、
 * 不支持读方法经视图同样抛出、toReadView 封存写侧（此后 put / remove 一律抛
 * IllegalStateException，封存前注册的内容对视图可见）。
 */
class RestRadixTreeReadViewTest {

    @Test
    void toReadViewReturnsSameInstanceOnRepeatedCalls() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertSame(view, tree.toReadView());
        assertThrows(IllegalStateException.class, () -> tree.put("/b", 2),
                "重复建视图封存状态不变");
    }

    @Test
    void readViewDelegatesMatchAnyWithValueAndParams() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/:id", 1);
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        RestRadixTree.MatchResult<Integer> hit = view.matchAny("/user/42");
        assertEquals(1, hit.value());
        assertEquals(Map.of("id", "42"), hit.params());
    }

    @Test
    void writeOperationsAfterToReadViewAreRejected() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/a", 1);
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertEquals("The rest radix tree does not allow write operations after toReadView()",
                assertThrows(IllegalStateException.class, () -> tree.put("/b", 2)).getMessage());
        assertEquals("The rest radix tree does not allow write operations after toReadView()",
                assertThrows(IllegalStateException.class, () -> tree.remove("/a")).getMessage());
        assertEquals(1, view.matchAny("/a").value(), "封存前注册的内容对视图可见");
    }

    @Test
    void sealedWriteRejectionPrecedesArgumentValidation() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertThrows(IllegalStateException.class, () -> tree.put(null, 1),
                "封存先于参数校验");
        assertThrows(IllegalStateException.class, () -> tree.remove(null),
                "封存先于参数校验");
        assertNull(view.matchAny("/a"), "封存空树读取不受影响");
    }

    @Test
    void readViewBacktracksAndNormalizesLikeWriteSide() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        tree.put("/user/admin", 1);
        tree.put("/:param/posts", 2);
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertEquals(1, view.matchAny("//user//admin/").value(), "归一化与字面优先同写侧");
        assertEquals(2, view.matchAny("/user/posts").value(), "回溯命中参数分支同写侧");
    }

    @Test
    void readViewRejectsNullPathLikeWriteSide() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertEquals("The path argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.matchAny(null)).getMessage());
    }

    @Test
    void readViewUnsupportedReadMethodsThrowLikeOwner() {
        RestRadixTree<Integer> tree = new RestRadixTree<>();
        RadixTree<RestRadixTree.MatchResult<Integer>> view = tree.toReadView();
        assertEquals("The rest radix tree does not support the get operation",
                assertThrows(UnsupportedOperationException.class, () -> view.get("/a")).getMessage());
        assertEquals("The rest radix tree does not support the exist operation",
                assertThrows(UnsupportedOperationException.class, () -> view.exist("/a")).getMessage());
        assertEquals("The rest radix tree does not support the match operation",
                assertThrows(UnsupportedOperationException.class, () -> view.match("/a")).getMessage());
    }
}
