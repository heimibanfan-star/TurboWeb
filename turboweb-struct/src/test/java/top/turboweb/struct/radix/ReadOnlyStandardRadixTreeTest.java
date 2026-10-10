package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReadOnlyStandardRadixTree} 只读视图：同一实例缓存、活视图可见性与读委托。
 */
class ReadOnlyStandardRadixTreeTest {

    @Test
    void toReadViewReturnsSameInstanceOnRepeatCalls() {
        WriteAbleStandardRadixTree<String> tree = new WriteAbleStandardRadixTree<>();
        assertSame(tree.toReadView(), tree.toReadView());
    }

    @Test
    void readViewSeesSubsequentWritesAndRemoves() {
        WriteAbleStandardRadixTree<String> tree = new WriteAbleStandardRadixTree<>();
        tree.put("abc", "1");
        MutiRadixTree<String> view = tree.toReadView();
        assertEquals("1", view.get("abc"));
        assertTrue(view.exist("abc"));
        tree.put("ab", "2");
        assertEquals("2", view.get("ab"));
        assertEquals(Set.of("2"), view.match("abd"));
        assertTrue(tree.remove("abc"));
        assertFalse(view.exist("abc"));
        assertEquals(Set.of("2"), view.match("abc"));
    }

    @Test
    void readViewDelegatesNullKeyValidation() {
        WriteAbleStandardRadixTree<String> tree = new WriteAbleStandardRadixTree<>();
        MutiRadixTree<String> view = tree.toReadView();
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.get(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.exist(null)).getMessage());
        assertEquals("The key argument was null",
                assertThrows(IllegalArgumentException.class, () -> view.match(null)).getMessage());
    }
}
