package top.turboweb.commons.serializer;

import java.util.List;
import java.util.Map;

/**
 * json序列化器
 */
public interface JsonSerializer {

    /**
     * 将 Map 映射为 JavaBean。
     *
     * @param map          要映射的 Map
     * @param beanClass    JavaBean 的 Class 对象
     * @return             映射后的 JavaBean 对象
     */
    <T> T mapToBean(Map<String, Object> map, Class<T> beanClass);

    /**
     * 将 JavaBean 映射为 Map。
     *
     * @param bean 要映射的 JavaBean 对象
     * @return 映射后的 Map 对象
     */
    Map<?, ?> beanToMap(Object bean);

    /**
     * 将 JavaBean 转换为 JSON 字符串。
     *
     * @param bean         要转换的 JavaBean 对象
     * @return             转换后的 JSON 字符串
     */
    String beanToJson(Object bean);

    /**
     * 将 JSON 字符串转换为 JavaBean。
     *
     * @param json         要转换的 JSON 字符串
     * @param beanClass    JavaBean 的 Class 对象
     * @return             转换后的 JavaBean 对象
     */
    <T> T jsonToBean(String json, Class<T> beanClass);

    /**
     * 将 JSON 字符串转换为 Map。
     *
     * @param json         要转换的 JSON 字符串
     * @return             转换后的 Map 对象
     */
    Map<?, ?> jsonToMap(String json);

    /**
     * 将 JSON 列表转换为 JavaBean 列表。
     *
     * @param json         要转换的 JSON 列表字符串
     * @param beanClass    JavaBean 的 Class 对象
     * @return             转换后的 JavaBean 列表
     */
    <T> List<T> jsonToList(String json, Class<T> beanClass);

    /**
     * 深度克隆一个 JavaBean。
     *
     * @param bean 要克隆的 JavaBean 对象
     * @return     克隆后的 JavaBean 对象
     */
    <T> T deepinClone(T bean);

    /**
     * 将 JSON 字符串更新到一个 JavaBean 中。
     *
     * @param json  JSON 字符串
     * @param bean  要更新的 JavaBean 对象
     */
    <T> T jsonUpdateBean(String json, T bean);
}
