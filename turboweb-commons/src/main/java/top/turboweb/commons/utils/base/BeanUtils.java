package top.turboweb.commons.utils.base;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboParamParseException;

import java.lang.reflect.Method;
import java.util.Map;

public class BeanUtils {

    private static final Logger log = LoggerFactory.getLogger(BeanUtils.class);
    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 将 Map 转换为 JavaBean。忽略没有对应属性的字段。
     *
     * @param map    要转换的 Map，键为属性名，值为属性值
     * @param bean   要填充的 JavaBean 对象
     */
    public static void mapToBean(Map<String, Object> map, Object bean) {
        if (map == null || bean == null) {
            return;
        }

        // 遍历 Map 中的每个键值对
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();

            try {
                // 尝试找到与属性名匹配的 setter 方法
                Method setter = findSetter(bean.getClass(), propertyName);

                // 如果找到对应的 setter 方法，则调用它进行设置
                if (setter != null) {
                    // 转换 Map 中的值类型为 setter 方法所需要的类型
                    Class<?> paramType = setter.getParameterTypes()[0];
                    Object convertedValue = convertValue(value, paramType);
                    setter.invoke(bean, convertedValue);
                }
            } catch (Exception e) {
                log.error("Ignoring property: {} due to error: {}", propertyName, e.getMessage());
                throw new TurboParamParseException(e);
            }
        }
    }

    /**
     * 查找匹配的 setter 方法
     *
     * @param clazz        JavaBean 类
     * @param propertyName 属性名
     * @return setter 方法，若没有找到则返回 null
     */
    private static Method findSetter(Class<?> clazz, String propertyName) {
        String setterName = "set" + capitalize(propertyName);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    /**
     * 将首字母大写（用于构建 setter 方法名）
     *
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 转换值为 setter 方法的参数类型
     *
     * @param value      Map 中的值
     * @param paramType  setter 方法的参数类型
     * @return 转换后的值
     * @throws Exception 转换异常
     */
    private static Object convertValue(Object value, Class<?> paramType) throws Exception {
        if (value == null) {
            return null;
        }

        if (paramType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // 支持基本类型的转换（例如 String -> int）
        if (paramType == Integer.class || paramType == int.class) {
            return Integer.parseInt(value.toString());
        } else if (paramType == Double.class || paramType == double.class) {
            return Double.parseDouble(value.toString());
        } else if (paramType == Boolean.class || paramType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (paramType == Long.class || paramType == long.class) {
            return Long.parseLong(value.toString());
        } else if (paramType == String.class) {
            return value.toString();
        }

        // 其他类型的转换可以按需扩展
        throw new TurboParamParseException("Unsupported conversion for type: " + paramType.getName());
    }

    /**
     * 获取 ObjectMapper 实例
     *
     * @return ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
