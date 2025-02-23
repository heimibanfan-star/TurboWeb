package org.turbo.web.utils.common;

import org.turbo.web.constants.TypeConstants;

/**
 * 类型相关工具类
 */
public class TypeUtils {

    private TypeUtils() {
    }

    /**
     * 判断是否是包装类型
     *
     * @param className 类型
     * @return 是否是包装类型
     */
    public static boolean isWrapperType(String className) {
        return TypeConstants.WRAPPER_TYPE.contains(className);
    }
}
