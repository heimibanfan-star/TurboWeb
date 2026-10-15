package top.turboweb.common.util;

import java.util.List;

/**
 * 类型相关工具类
 */
public class JavaTypeUtil {

    /**
     * 包装类型：8 个基本类型包装类 + String + Void，共 10 个唯一值。
     * <br>元素须保持无重复：本类 {@code isWrapperType} 走 {@code contains}
     * 判定，重复项不影响判定结果，但会扭曲该表自身的计数语义。
     */
    public static final List<String> WRAPPER_TYPE = List.of(
        Boolean.class.getName(),
        Byte.class.getName(),
        Short.class.getName(),
        Integer.class.getName(),
        Long.class.getName(),
        Float.class.getName(),
        Double.class.getName(),
        Character.class.getName(),
        String.class.getName(),
        Void.class.getName()
    );

    private JavaTypeUtil() {
    }

    /**
     * 判断是否是包装类型
     *
     * @param className 类型
     * @return 是否是包装类型
     */
    public static boolean isWrapperType(String className) {
        return WRAPPER_TYPE.contains(className);
    }
}
