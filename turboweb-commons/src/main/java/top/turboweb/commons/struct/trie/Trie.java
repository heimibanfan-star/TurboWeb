package top.turboweb.commons.struct.trie;

import java.util.Map;
import java.util.Set;

/**
 * 前缀树
 */
public interface Trie <T, M> extends Iterable<T> {

    /**
     * 插入元素
     * @param key 元素的key
     * @param value 元素的值
     * @param overwrite 是否覆盖
     */
    void insert(String key, T value, boolean overwrite);

    /**
     * 插入元素
     *
     * @param key 元素的key
     * @param value 元素的值
     */
    void insert(String key, T value);

    /**
     * 判断元素是否存在
     * @param key 元素的key
     * @return 是否存在
     */
    boolean exists(String key);

    /**
     * 根据输入的key精确匹配元素
     * @param key 元素的key
     * @return 元素的值
     */
    T get(String key);

    /**
     * 根据具体的子类实现的规则进行元素的匹配
     * @param key 元素的key
     * @return 匹配的元素
     */
    M match(String key);

    /**
     * 删除元素
     * @param key 元素的key
     */
    void delete(String key);

    /**
     * 获取所有元素
     * @return 所有元素
     */
    Map<String, T> all();

    /**
     * 获取所有根key
     *
     * @return 所有根key
     */
    Set<String> roots();

    /**
     * 获取元素数量
     * @return 元素数量
     */
    int size();
}
