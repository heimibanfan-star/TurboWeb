package top.turboweb.http.router.parameter;

import top.turboweb.serializer.JsonSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数处理助手：单值形参的 {@link SimpleTypeHandler} 转换器表与多值参数映射到 bean 的绑定通道，
 * 供各参数解析器与绑定器共用。
 */
public class ParameterHandleHelper {

    private ParameterHandleHelper() {
    }

    private static final Map<Class<?>, SimpleTypeHandler> SIMPLE_TYPE_HANDLERS = new HashMap<>();

    static {
        SIMPLE_TYPE_HANDLERS.put(String.class, value -> value);
        SIMPLE_TYPE_HANDLERS.put(Integer.class, value -> value == null ? null : Integer.parseInt(value));
        SIMPLE_TYPE_HANDLERS.put(int.class, value -> value == null ? 0 : Integer.parseInt(value));
        SIMPLE_TYPE_HANDLERS.put(Long.class, value -> value == null ? null : Long.parseLong(value));
        SIMPLE_TYPE_HANDLERS.put(long.class, value -> value == null ? 0L : Long.parseLong(value));
        SIMPLE_TYPE_HANDLERS.put(Double.class, value -> value == null ? null : Double.parseDouble(value));
        SIMPLE_TYPE_HANDLERS.put(double.class, value -> value == null ? 0.0 : Double.parseDouble(value));
        SIMPLE_TYPE_HANDLERS.put(Boolean.class, value -> value == null ? null : Boolean.parseBoolean(value));
        SIMPLE_TYPE_HANDLERS.put(boolean.class, Boolean::parseBoolean);
        SIMPLE_TYPE_HANDLERS.put(LocalDate.class, value -> value == null ? null : LocalDate.parse(value));
        SIMPLE_TYPE_HANDLERS.put(LocalDateTime.class, value -> value == null ? null : LocalDateTime.parse(value));
        SIMPLE_TYPE_HANDLERS.put(LocalTime.class, value -> value == null ? null : LocalTime.parse(value));
    }

    /**
     * 按形参声明类型获取简单类型处理器
     *
     * @param clazz 类型
     * @return 处理器；表内无该类型时返回 null，表示此类型不经本通道转化
     */
    public static SimpleTypeHandler getSimpleTypeHandler(Class<?> clazz) {
        return SIMPLE_TYPE_HANDLERS.get(clazz);
    }

    /**
     * 折叠多值参数映射并经序列化器绑定为 bean：值列表仅一个元素时折叠为该元素（可为 null），
     * 多个元素保留列表，折叠后的映射交由 json 序列化器完成属性映射。
     *
     * @param jsonSerializer json 序列化器，完成属性映射
     * @param paramsMap 参数名到值列表的映射，可为空
     * @param beanType 目标 bean 类型
     * @return 绑定后的 bean；未知属性按序列化器忽略未知字段的口径跳过
     */
    public static <T> T bindBeanMap(JsonSerializer jsonSerializer, Map<String, List<String>> paramsMap,
                                    Class<T> beanType) {
        Map<String, Object> collapsed = new HashMap<>();
        paramsMap.forEach((key, value) -> collapsed.put(key, value.size() == 1 ? value.getFirst() : value));
        return jsonSerializer.mapToBean(collapsed, beanType);
    }
}
