package top.turboweb.common.utils.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包装类型判定（数据源是 TypeConstants.WRAPPER_TYPE，见 F11）。
 */
class TypeUtilsTest {

    @Test
    void wrapperClassNamesAreRecognised() {
        assertTrue(TypeUtils.isWrapperType(Integer.class.getName()));
        assertTrue(TypeUtils.isWrapperType(String.class.getName()));
        assertTrue(TypeUtils.isWrapperType(Boolean.class.getName()));
    }

    @Test
    void primitivesAndArbitraryClassesAreNotWrappers() {
        assertFalse(TypeUtils.isWrapperType("int"), "primitive 名不在清单里");
        assertFalse(TypeUtils.isWrapperType(Object.class.getName()));
        // 清单里有 java.lang.Void：Void 不是任何 primitive 的包装，但判定返回 true
        assertTrue(TypeUtils.isWrapperType(Void.class.getName()), "钉住当前清单内容，阶段 5 决定是否剔除");
    }
}
