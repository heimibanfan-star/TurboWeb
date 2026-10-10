package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 随机操作序列回放：以朴素参照实现对账 put / remove / match 的交错组合，
 * 固定种子保证失败可复现。生成器只产合法格式模式串（格式校验由单测钉死，
 * 参照不含格式与模式内重名校验逻辑，但含同槽异名冲突语义）。
 * <p>参照 match 取"字面 0 参数 1"类型签名最小（即字面优先、参数兜底、失败回溯
 * 的深度优先顺序）的首个候选，与实现的回溯 DFS 等价。
 */
class RestRadixTreeRandomizedTest {

    private static final List<String> PATTERN_LITERALS = List.of("a", "b", "c");
    private static final List<String> PARAM_NAMES = List.of("x", "y", "z");
    private static final List<String> PATH_ALPHABET = List.of("a", "b", "c", "d", "e");
    private static final int ROUNDS = 8;
    private static final int OPERATIONS_PER_ROUND = 400;
    private static final int PROBES_PER_OPERATION = 5;

    @Test
    void randomOperationSequencesMatchReferenceImplementation() {
        for (long seed = 0; seed < ROUNDS; seed++) {
            replay(seed);
        }
    }

    private void replay(long seed) {
        Random random = new Random(seed);
        WriteAbleRestRadixTree<Integer> tree = new WriteAbleRestRadixTree<>();
        ReferenceTrie reference = new ReferenceTrie();
        for (int op = 0; op < OPERATIONS_PER_ROUND; op++) {
            String pattern = randomPattern(random);
            if (random.nextInt(100) < 55) {
                int value = random.nextInt();
                if (reference.conflictWith(pattern) != null) {
                    assertThrows(IllegalArgumentException.class, () -> tree.put(pattern, value),
                            seed + " 轮 put 应抛同槽冲突, op=" + op);
                } else {
                    Integer expected = reference.put(pattern, value);
                    assertEquals(expected, tree.put(pattern, value), seed + " 轮 put 返回值, op=" + op);
                }
            } else {
                boolean expected = reference.remove(pattern);
                assertEquals(expected, tree.remove(pattern), seed + " 轮 remove 返回值, op=" + op);
            }
            assertMatchEquals(reference, tree, concretePath(pattern, random), seed, op);
            for (int probe = 0; probe < PROBES_PER_OPERATION; probe++) {
                assertMatchEquals(reference, tree, randomPath(random, 1, 4), seed, op);
            }
        }
    }

    private void assertMatchEquals(ReferenceTrie reference, WriteAbleRestRadixTree<Integer> tree,
                                   String path, long seed, int op) {
        ReferenceHit expected = reference.match(path);
        RestRadixTree.MatchResult<Integer> actual = tree.match(path);
        if (expected == null) {
            assertNull(actual, seed + " 轮 match 应未命中, path=" + path + ", op=" + op);
            return;
        }
        assertNotNull(actual, seed + " 轮 match 应命中, path=" + path + ", op=" + op);
        assertEquals(expected.value, actual.value, seed + " 轮 match 值, path=" + path + ", op=" + op);
        assertEquals(expected.params, actual.params, seed + " 轮 match 参数绑定, path=" + path + ", op=" + op);
    }

    /**
     * 随机模式串：1 到 4 段，段以四成概率为参数段（模式内参数名不重复，
     * 参数名额度用尽即改产字面段），字面段取自小字母表抬高共用前缀概率。
     */
    private String randomPattern(Random random) {
        int length = 1 + random.nextInt(4);
        Set<String> usedNames = new HashSet<>();
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < length; i++) {
            pattern.append('/');
            boolean param = random.nextInt(100) < 40 && usedNames.size() < PARAM_NAMES.size();
            if (param) {
                List<String> available = new ArrayList<>(PARAM_NAMES);
                available.removeAll(usedNames);
                String name = available.get(random.nextInt(available.size()));
                usedNames.add(name);
                pattern.append(':').append(name);
            } else {
                pattern.append(PATTERN_LITERALS.get(random.nextInt(PATTERN_LITERALS.size())));
            }
        }
        return pattern.toString();
    }

    /**
     * 随机路径：1 到 4 段的净形路径（单首斜杠、无空段），
     * 字母表含模式字面之外的字以抬高未命中概率。
     */
    private String randomPath(Random random, int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < length; i++) {
            path.append('/').append(PATH_ALPHABET.get(random.nextInt(PATH_ALPHABET.size())));
        }
        return path.toString();
    }

    /**
     * 模式串具象化为同段数路径：参数段替换为随机字面段，
     * 作为探针可高频命中已注册形态。
     */
    private String concretePath(String pattern, Random random) {
        String[] segments = pattern.substring(1).split("/", -1);
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            path.append('/');
            if (segment.charAt(0) == ':') {
                path.append(PATH_ALPHABET.get(random.nextInt(PATH_ALPHABET.size())));
            } else {
                path.append(segment);
            }
        }
        return path.toString();
    }

    /**
     * 参照命中：命中值与参数绑定。
     */
    private static final class ReferenceHit {

        private final Integer value;
        private final Map<String, String> params;

        private ReferenceHit(Integer value, Map<String, String> params) {
            this.value = value;
            this.params = params;
        }
    }

    /**
     * 朴素参照实现：注册表为模式串到值的映射。
     * put 含同槽异名冲突判定（逐段相容走到双参数且异名即冲突），
     * match 筛选段数相同的已注册模式，取类型签名最小者为命中。
     */
    private static final class ReferenceTrie {

        private final Map<String, Integer> patterns = new HashMap<>();

        Integer put(String pattern, Integer value) {
            return patterns.put(pattern, value);
        }

        boolean remove(String pattern) {
            return patterns.remove(pattern) != null;
        }

        /**
         * 返回与新模式发生同槽异名参数冲突的既有模式；无冲突返回 null。
         */
        String conflictWith(String pattern) {
            for (String existing : patterns.keySet()) {
                if (conflicts(existing, pattern)) {
                    return existing;
                }
            }
            return null;
        }

        private boolean conflicts(String first, String second) {
            String[] firstSegments = split(first);
            String[] secondSegments = split(second);
            int common = Math.min(firstSegments.length, secondSegments.length);
            for (int i = 0; i < common; i++) {
                String a = firstSegments[i];
                String b = secondSegments[i];
                if (a.equals(b)) {
                    continue;
                }
                if (a.charAt(0) == ':' && b.charAt(0) == ':') {
                    return true;
                }
                return false;
            }
            return false;
        }

        ReferenceHit match(String path) {
            String[] segments = split(path);
            ReferenceHit best = null;
            String bestSignature = null;
            for (Map.Entry<String, Integer> entry : patterns.entrySet()) {
                String[] patternSegments = split(entry.getKey());
                if (patternSegments.length != segments.length) {
                    continue;
                }
                StringBuilder signature = new StringBuilder();
                Map<String, String> params = new HashMap<>();
                boolean compatible = true;
                for (int i = 0; i < segments.length; i++) {
                    String patternSegment = patternSegments[i];
                    if (patternSegment.charAt(0) == ':') {
                        signature.append('1');
                        params.put(patternSegment.substring(1), segments[i]);
                    } else if (patternSegment.equals(segments[i])) {
                        signature.append('0');
                    } else {
                        compatible = false;
                        break;
                    }
                }
                if (compatible && (bestSignature == null || signature.toString().compareTo(bestSignature) < 0)) {
                    bestSignature = signature.toString();
                    best = new ReferenceHit(entry.getValue(), params);
                }
            }
            return best;
        }

        private static String[] split(String path) {
            if (path.equals("/")) {
                return new String[0];
            }
            return path.substring(1).split("/", -1);
        }
    }
}
