package top.turboweb.commons.struct.trie;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 支持路径参数和通配符的路径匹配前缀树接口。
 * 支持：
 *  - 多级通配符 "**"
 *  - 单级通配符 "*"
 *  - 路径参数 "{name}" 或 "{name:type}"，type支持num、bool、str
 *  可提取路径参数值。
 *
 * @param <V> 挂载内容类型
 */
@Deprecated
public interface PathTrie<V> {

    /**
     * 路径匹配结果，包含匹配值及路径参数
     */
    class MatchResult<V> {
        private final V value;
        private final Map<String, String> params;

        public MatchResult(V value, Map<String, String> params) {
            this.value = value;
            this.params = params;
        }

        /** 返回匹配的内容 */
        public V getValue() {
            return value;
        }

        /** 返回路径参数名与实际值映射 */
        public Map<String, String> getParams() {
            return params;
        }
    }

    /**
     * 插入路径及对应内容
     * @param path 路径，支持通配符和参数，必须以 '/' 开头
     * @param value 挂载内容
     * @throws IllegalArgumentException 路径格式错误
     */
    void insert(String path, V value);

    /**
     * 查找匹配路径及参数值
     * 只支持{param:type}匹配不支持*和**
     * @param path 请求路径
     * @return 匹配结果或空
     */
    Optional<MatchResult<V>> paramMatch(String path);

    /**
     * 是否存在完全匹配的路径（无通配符）
     * @param path 路径
     * @return 是否存在
     */
    boolean containsExact(String path);

    /**
     * 删除指定路径及挂载内容
     * @param path 路径
     * @return 删除成功返回true
     */
    boolean delete(String path);

    /**
     * 获取所有以prefix开头的路径及挂载内容
     * @param prefix 路径前缀
     * @return 路径和内容映射
     */
    Map<String, V> getEntriesWithPrefix(String prefix);

    /**
     * 获取所有存储的路径
     * @return 路径列表
     */
    List<String> allPaths();

    /**
     * 只支持*和**模式的匹配，不支持{param:type}
     * @param path 请求路径
     * @return 匹配结果列表
     */
    Set<V> patternMatch(String path);
}
