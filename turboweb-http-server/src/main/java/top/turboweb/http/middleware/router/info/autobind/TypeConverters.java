package top.turboweb.http.middleware.router.info.autobind;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 类型转换器
 */
public class TypeConverters {

    private TypeConverters() {
    }

    @FunctionalInterface
    public interface Converter {
        Object convert(String value);
    }


    private static final Map<Class<?>, Converter> CONVERTERS = new HashMap<>();

    static {
        CONVERTERS.put(String.class, value -> value);
        CONVERTERS.put(Integer.class, value -> value == null ? null : Integer.parseInt(value));
        CONVERTERS.put(int.class, value -> value == null ? 0 : Integer.parseInt(value));
        CONVERTERS.put(Long.class, value -> value == null ? null : Long.parseLong(value));
        CONVERTERS.put(long.class, value -> value == null ? 0L : Long.parseLong(value));
        CONVERTERS.put(Double.class, value -> value == null ? null : Double.parseDouble(value));
        CONVERTERS.put(double.class, value -> value == null ? 0.0 : Double.parseDouble(value));
        CONVERTERS.put(Boolean.class, value -> value == null ? null : Boolean.parseBoolean(value));
        CONVERTERS.put(boolean.class, Boolean::parseBoolean);
        CONVERTERS.put(LocalDate.class, value -> value == null ? null : LocalDate.parse(value));
        CONVERTERS.put(LocalDateTime.class, value -> value == null ? null : LocalDateTime.parse(value));
        CONVERTERS.put(LocalTime.class, value -> value == null ? null : LocalTime.parse(value));
    }

    /**
     * 获取类型转换器
     *
     * @param clazz 类型
     * @return 转换器
     */
    public static Converter getConverter(Class<?> clazz) {
        return CONVERTERS.get(clazz);
    }

}
