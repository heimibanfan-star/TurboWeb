package top.turboweb.commons.constants;

import java.util.List;

/**
 * 类型相关常量
 */
public class TypeConstants {

    /**
     * 包装类型：8 个基本类型包装类 + String + Void，共 10 个唯一值。
     * <br>元素须保持无重复：唯一生产消费点 {@code TypeUtils.isWrapperType} 走
     * {@code contains} 判定，重复项不影响判定结果，但会扭曲该表自身的计数语义。
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
