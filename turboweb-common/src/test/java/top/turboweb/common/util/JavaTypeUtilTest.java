package top.turboweb.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包装类型判定（数据源是本类 WRAPPER_TYPE 清单，见 F11）。
 */
class JavaTypeUtilTest {

    @Test
    void wrapperClassNamesAreRecognised() {
        assertTrue(JavaTypeUtil.isWrapperType(Integer.class.getName()));
        assertTrue(JavaTypeUtil.isWrapperType(String.class.getName()));
        assertTrue(JavaTypeUtil.isWrapperType(Boolean.class.getName()));
    }

    @Test
    void primitivesAndArbitraryClassesAreNotWrappers() {
        assertFalse(JavaTypeUtil.isWrapperType("int"), "primitive 名不在清单里");
        assertFalse(JavaTypeUtil.isWrapperType(Object.class.getName()));
        // 清单里有 java.lang.Void：Void 不是任何 primitive 的包装，但判定返回 true
        assertTrue(JavaTypeUtil.isWrapperType(Void.class.getName()), "钉住当前清单内容，阶段 5 决定是否剔除");
    }

    @Test
    @DisplayName("F11 已修：WRAPPER_TYPE 去重，10 个元素、10 个唯一值")
    void wrapperTypeListIsDuplicateFree() {
        // F11 修正后期望正确行为：重复的 String.class.getName() 已移除，
        // 列表长度即唯一值数，String 恰出现一次。
        assertEquals(10, JavaTypeUtil.WRAPPER_TYPE.size());
        assertEquals(10, new HashSet<>(JavaTypeUtil.WRAPPER_TYPE).size());
        assertEquals(1, Collections.frequency(JavaTypeUtil.WRAPPER_TYPE, String.class.getName()));
    }
}
