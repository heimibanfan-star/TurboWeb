package top.turboweb.commons.struct.trie;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 支持*和**匹配的 trie
 */
public class PatternUrlTrie<T> extends UrlTrie<T, Set<T>> {

    // 修改 VALID_KEY 正则，允许**后接静态段
    private static final Pattern VALID_KEY = Pattern.compile(
            "^(/([A-Za-z0-9_]+|\\*{1,2}))+$"
    );

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

    @Override
    protected Set<T> doMatch(String[] segs) {
        Set<T> result = new HashSet<>();
        dfs(root, 0, segs, result);
        return result;
    }


    /**
     * 深度优先搜索匹配所有符合规则的节点
     * @param node 当前遍历的节点
     * @param index 当前处理的路径段索引
     * @param segs 待匹配的路径段数组
     * @param result 收集匹配到的结果
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
     * 匹配结果类，用于记录匹配的详细信息
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
