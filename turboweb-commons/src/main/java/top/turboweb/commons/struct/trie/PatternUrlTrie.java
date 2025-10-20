package top.turboweb.commons.struct.trie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 支持*和**匹配的 trie
 */
public class PatternUrlTrie<T> extends UrlTrie<T, T> {

    private static final Pattern VALID_KEY = Pattern.compile(
            "^(/([A-Za-z]+|\\*{1,2})){1,}$"
    );

    @Override
    public void insert(String key, T value, boolean overwrite) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("key is invalid");
        }
        // 判断是否出现多次**
        int first = key.indexOf("**");
        if (first != -1 && (key.indexOf("**", first + 2) != -1 || key.indexOf("*", first + 2) != -1)) {
            throw new IllegalArgumentException("Invalid route pattern: '**' wildcard may appear only once per path: " + key);
        }
        super.insert(key, value, overwrite);
    }

    @Override
    protected T doMatch(String[] segs) {
        List<MatchResult> results = new ArrayList<>();
        // 启动回溯匹配，从根节点、第0个段开始
        backtrack(root, segs, 0, 0, 0, results);

        if (results.isEmpty()) {
            return null;
        }

        // 选择最佳匹配结果（保持原优先级逻辑）
        MatchResult bestMatch = results.getFirst();
        for (MatchResult result : results) {
            if (isBetterMatch(result, bestMatch)) {
                bestMatch = result;
            }
        }

        return bestMatch.value;
    }

    /**
     * 回溯算法匹配（调整通配符处理逻辑）
     * @param currentNode 当前节点
     * @param segs 路径段数组
     * @param segIndex 当前处理的路径段索引
     * @param exactMatches 精确匹配的数量
     * @param singleStarMatches *匹配的数量
     * @param results 匹配结果列表
     */
    private void backtrack(Node<T> currentNode, String[] segs, int segIndex,
                             int exactMatches, int singleStarMatches, List<MatchResult> results) {
        // 如果已经匹配到所有路径段，检查当前节点是否有值
        if (segIndex >= segs.length) {
            if (currentNode.value() != null) {
                results.add(new MatchResult(currentNode.value(), exactMatches, singleStarMatches));
            }
            // 检查当前节点是否有**子节点（处理**匹配0段的情况）
            Node<T> doubleStarNode = currentNode.subNodes().get("**");
            if (doubleStarNode != null && doubleStarNode.value() != null) {
                results.add(new MatchResult(doubleStarNode.value(), exactMatches, singleStarMatches));
            }
            return;
        }

        String currentSeg = segs[segIndex];
        Map<String, Node<T>> subNodes = currentNode.subNodes();

        // 1. 精确匹配：直接匹配当前段，推进索引
        if (subNodes.containsKey(currentSeg)) {
            Node<T> nextNode = subNodes.get(currentSeg);
            backtrack(nextNode, segs, segIndex + 1, exactMatches + 1, singleStarMatches, results);
        }

        // 2. * 匹配：必须匹配至少一段（当前段），因此索引必须+1
        if (subNodes.containsKey("*")) {
            Node<T> starNode = subNodes.get("*");
            backtrack(starNode, segs, segIndex + 1, exactMatches, singleStarMatches + 1, results);
        }

        // 3. **匹配：支持0段或多段
        if (subNodes.containsKey("**")) {
            Node<T> doubleStarNode = subNodes.get("**");

            // a. **匹配0段：直接使用**节点的值（如果有），不消耗当前段
            if (doubleStarNode.value() != null) {
                results.add(new MatchResult(doubleStarNode.value(), exactMatches, singleStarMatches));
            }

            // b. **匹配多段：继续用**匹配下一段（消耗当前段）
            backtrack(doubleStarNode, segs, segIndex + 1, exactMatches, singleStarMatches, results);

            // c. **后续可能有其他路径段，尝试匹配**之后的子节点
            for (Node<T> child : doubleStarNode.subNodes().values()) {
                if (child.key().equals(currentSeg) || child.key().equals("*") || child.key().equals("**")) {
                    int newExact = exactMatches + (child.key().equals(currentSeg) ? 1 : 0);
                    int newSingleStar = singleStarMatches + (child.key().equals("*") ? 1 : 0);
                    int newSegIndex = child.key().equals("**") ? segIndex : segIndex + 1;
                    backtrack(child, segs, newSegIndex, newExact, newSingleStar, results);
                }
            }
        }
    }

    /**
     * 判断是否是更好的匹配
     * @param candidate 候选匹配结果
     * @param currentBest 当前最佳匹配结果
     * @return 是否是更好的匹配
     */
    private boolean isBetterMatch(MatchResult candidate, MatchResult currentBest) {
        // 优先级：精确匹配 > * 匹配 > ** 匹配
        if (candidate.exactMatches > currentBest.exactMatches) {
            return true;
        } else if (candidate.exactMatches == currentBest.exactMatches) {
            if (candidate.singleStarMatches > currentBest.singleStarMatches) {
                return true;
            } else if (candidate.singleStarMatches == currentBest.singleStarMatches) {
                // 当其他条件条件相同时，选择路径较短的匹配
                int candidatePathLength = candidate.exactMatches + candidate.singleStarMatches;
                int currentBestPathLength = currentBest.exactMatches + currentBest.singleStarMatches;
                return candidatePathLength < currentBestPathLength;
            }
        }
        return false;
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
