package top.turboweb.commons.struct.trie;

import java.util.Map;
import java.util.Set;

/**
 * 通用前缀树接口（Trie）
 *
 * <p>用于存储基于字符串路径的层级数据结构。
 * 支持插入、删除、精确查找和自定义匹配等操作。
 *
 * @param <T> 节点存储的值类型
 * @param <M> 匹配结果类型（由具体实现定义）
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
     * 判断指定键是否存在。
     *
     * @param key 要检查的键
     * @return 若键存在返回 {@code true}，否则返回 {@code false}
     */
    boolean exists(String key);

    /**
     * 根据键进行精确匹配。
     *
     * @param key 要查询的键
     * @return 匹配到的值，若不存在则返回 {@code null}
     */
    T get(String key);

    /**
     * 按实现定义的规则进行匹配（例如通配符、路径参数等）。
     *
     * @param key 输入键
     * @return 匹配结果，由具体实现定义
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
