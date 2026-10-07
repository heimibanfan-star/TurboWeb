package top.turboweb.commons.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 常量表：ANSI 码与匹配/参数类型常量是装配期手写的对账单，值一旦改动这里必须红。
 */
class ConstantsTest {

    @Test
    void fontColorsAreAnsiEscapes() {
        assertEquals("\033[0m", FontColors.RESET);
        assertEquals("\033[31m", FontColors.RED);
        assertEquals("\033[32m", FontColors.GREEN);
        assertEquals("\033[33m", FontColors.YELLOW);
        assertEquals("\033[34m", FontColors.BLUE);
        assertEquals("\033[35m", FontColors.MAGENTA);
        assertEquals("\033[36m", FontColors.CYAN);
        assertEquals("\033[37m", FontColors.WHITE);
        assertEquals("\033[30m", FontColors.BLACK);
    }

    @Test
    void matchAndParameterTypeConstants() {
        assertEquals("path", MatchType.PATH);
        assertEquals("complete", MatchType.COMPLETE);
        assertEquals("path", ParameterType.PATH);
        assertEquals("query", ParameterType.QUERY);
        assertEquals("body", ParameterType.BODY);
    }

    @Test
    @DisplayName("钉住 F11：WRAPPER_TYPE 有重复的 String 项，11 个元素只有 10 个唯一值")
    void wrapperTypeListContainsDuplicateStringEntry() {
        // 钉住 F11：若将来修正，本用例应改为期望正确行为
        assertEquals(11, TypeConstants.WRAPPER_TYPE.size());
        assertEquals(10, new HashSet<>(TypeConstants.WRAPPER_TYPE).size());
        assertEquals(2, Collections.frequency(TypeConstants.WRAPPER_TYPE, String.class.getName()));
    }
}
