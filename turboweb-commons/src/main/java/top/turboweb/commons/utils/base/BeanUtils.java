package top.turboweb.commons.utils.base;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class BeanUtils {

    private static final Logger log = LoggerFactory.getLogger(BeanUtils.class);
    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    /**
     * 将 Map 映射为 JavaBean。
     *
     * @param map          要映射的 Map
     * @param beanClass    JavaBean 的 Class 对象
     * @return             映射后的 JavaBean 对象
     */
    public static <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
        return OBJECT_MAPPER.convertValue(map, beanClass);
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
