package top.turboweb.commons.constants;

import java.util.List;
import java.util.Map;

/**
 * 类型相关常量
 */
public class TypeConstants {

    /**
     * 包装类型：8 个基本类型包装类 + String + Void，共 10 个唯一值。
     * <br>修 F11：原表尾第 11 项与第 9 项重复（同为 {@code String.class.getName()}），已移除；
     * 唯一生产消费点 {@code TypeUtils.isWrapperType} 走 {@code contains} 判定，去重行为等价。
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
}
