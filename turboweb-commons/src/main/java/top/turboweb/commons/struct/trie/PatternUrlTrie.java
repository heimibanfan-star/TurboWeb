package top.turboweb.commons.struct.trie;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 支持通配符匹配的 URL 前缀树实现。
 *
 * <p>支持以下两种通配符：</p>
 * <ul>
 *     <li><b>*</b>：匹配单个路径段，例如：
 *         <pre>
 *             /user/*   可以匹配 /user/123 或 /user/abc，但不能匹配 /user/a/b
 *         </pre>
 *     </li>
 *     <li><b>**</b>：匹配任意层级的路径（包括零个路径段），例如：
 *         <pre>
 *             /files/**   可以匹配 /files/、/files/a、/files/a/b/c
 *         </pre>
 *     </li>
 * </ul>
 *
 * <p>规则限制：</p>
 * <ul>
 *     <li>路径必须以斜杠（/）开头</li>
 *     <li>路径段只允许字母、数字、下划线或通配符 *、**</li>
 *     <li>“**” 只能出现一次，且后面不能再跟其他 * 通配符</li>
 * </ul>
 *
 * @param <T> 存储的值类型
 */
public class PatternUrlTrie<T> extends UrlTrie<T, Set<T>> {

    /**
     * 验证 key 的正则：
     * 仅允许由 / 开头，后面是由英数字、下划线或 *、** 构成的路径段。
     */
    private static final Pattern VALID_KEY = Pattern.compile(
            "^(/([A-Za-z0-9_]+|\\*{1,2}))+$"
    );

    /**
     * 插入路径。
     * <p>在插入前对路径格式和通配符规则进行校验：</p>
     * <ul>
     *     <li>路径必须符合 {@link #VALID_KEY}</li>
     *     <li>“**” 只能出现一次</li>
     *     <li>“**” 后不能再出现任何 “*” 或 “**”</li>
     * </ul>
     */
    @Override
    public void insert(String key, T value, boolean overwrite) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("key is invalid: " + key);
        }
        // 判断**是否出现多次或之后有通配符
        int firstMulti = key.indexOf("**");
        if (firstMulti != -1) {
            // 检查是否有多个**
            if (key.indexOf("**", firstMulti + 2) != -1) {
                throw new IllegalArgumentException("'**' can only appear once: " + key);
            }
            // 检查**之后是否有任何*（包括*和**）
            String suffix = key.substring(firstMulti + 2);
            if (suffix.contains("*")) {
                throw new IllegalArgumentException("'**' cannot be followed by '*' or '**': " + key);
            }
        }
        super.insert(key, value, overwrite);
    }

    /**
     * 通配符路径不需要额外处理，因此直接返回 null。
     * @param subKey 当前子路径片段
     * @return null（表示不做额外的参数转换）
     */
    @Override
    protected Details handleSubKey(String subKey) {
        return null;
    }

    /**
     * 根据路径匹配所有符合规则的节点。
     * <p>会返回所有可能匹配的结果集合，而非单一匹配。</p>
     *
     * @param segs 分割后的路径段数组
     * @return 匹配的所有值的集合
     */
    @Override
    protected Set<T> doMatch(String[] segs) {
        Set<T> result = new HashSet<>();
        dfs(root, 0, segs, result);
        return result;
    }


    /**
     * 深度优先搜索（DFS）匹配所有可能的节点。
     *
     * @param node   当前遍历的节点
     * @param index  当前处理的路径段索引
     * @param segs   目标路径的分段数组
     * @param result 用于收集匹配到的结果
     */
    private void dfs(Node<T> node, int index, String[] segs, Set<T> result) {
        // 若当前节点有值且已处理完所有路径段，加入结果
        if (node.value() != null && index == segs.length) {
            result.add(node.value());
        }

        // 遍历所有子节点
        for (Node<T> child : node.subNodes().values()) {
            String childKey = child.key();

            if (childKey.equals("*")) {
                // * 匹配单段（必须有剩余段）
                if (index < segs.length) {
                    dfs(child, index + 1, segs, result);
                }
            } else if (childKey.equals("**")) {
                // ** 匹配0到多段（从当前索引到末尾的所有可能）
                for (int i = index; i <= segs.length; i++) {
                    dfs(child, i, segs, result);
                }
            } else {
                // 静态段精确匹配
                if (index < segs.length && childKey.equals(segs[index])) {
                    dfs(child, index + 1, segs, result);
                }
            }
        }
    }

    /**
     * 匹配结果类（当前未使用，仅保留以备扩展）。
     * 可用于未来扩展匹配权重或优先级计算。
     */
    private class MatchResult {
        T value;
        int exactMatches;
        int singleStarMatches;

        MatchResult(T value, int exactMatches, int singleStarMatches) {
            this.value = value;
            this.exactMatches = exactMatches;
            this.singleStarMatches = singleStarMatches;
        }
    }

}
