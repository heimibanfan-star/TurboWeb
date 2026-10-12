package top.turboweb.commons.utils.base;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * uuid 工具：格式与唯一性。
 */
class RandomUtilsTest {

    @Test
    void uuidHasNoHyphensAndIs32Hex() {
        String id = RandomUtils.uuidWithoutHyphen();
        assertEquals(32, id.length());
        assertTrue(id.matches("^[0-9a-f]{32}$"), id);
    }

    @Test
    void consecutiveIdsDiffer() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            ids.add(RandomUtils.uuidWithoutHyphen());
        }
        assertEquals(64, ids.size());
    }
}
