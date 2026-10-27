package top.turboweb.http.router.parameter;

/**
 * 简单类型处理器：把解码后的单个字符串值转化为形参声明类型所需的对象。
 * 处理器实例由 {@code ParameterHandleHelper} 的转换器表按类型就位。
 */
@FunctionalInterface
public interface SimpleTypeHandler {

    /**
     * 转化字符串值
     *
     * @param value 待转化的字符串值，可为 null（各处理器按声明类型口径归一）
     * @return 转化后的值
     */
    Object handle(String value);
}
