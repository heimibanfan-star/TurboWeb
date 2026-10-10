package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StandardRadixTree} 只读视图 ReadView：同一实例缓存、活视图可见性与读委托。
 */
class StandardRadixTreeReadViewTest {

    @Test
    void toReadViewReturnsSameInstanceOnRepeatCalls() {
        StandardRadixTree<String> tree = new StandardRadixTree<>();
        assertSame(tree.toReadView(), tree.toReadView());
    }

    @Test
    void readViewSeesSubsequentWritesAndRemoves() {
        StandardRadixTree<String> tree = new StandardRadixTree<>();
        tree.put("abc", "1");
        RadixTree<String> view = tree.toReadView();
        assertEquals("1", view.get("abc"));
        assertTrue(view.exist("abc"));
        tree.put("ab", "2");
        assertEquals("2", view.get("ab"));
        assertEquals(Set.of("2"), view.match("abd"));
        assertEquals("2", view.matchAny("abd"), "matchAny 委托取最深命中");
        assertTrue(tree.remove("abc"));
        assertFalse(view.exist("abc"));
        assertEquals(Set.of("2"), view.match("abc"));
        assertEquals("2", view.matchAny("abc"), "remove 后视图仍按剩余键命中");
    }

    @Test
    void readViewDelegatesNullKeyValidation() {
        StandardRadixTree<String> tree = new StandardRadixTree<>();
        RadixTree<String> view = tree.toReadView();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.get(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.exist(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.match(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.matchAny(null)).getMessage());
    }
}
