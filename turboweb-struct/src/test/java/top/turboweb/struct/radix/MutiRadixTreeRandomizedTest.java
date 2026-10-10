package top.turboweb.struct.radix;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 随机操作序列回放：以 HashMap 朴素参照实现对账 get / exist / match，
 * 覆盖 put 拆分与 remove 合并的交错组合；固定种子保证失败可复现。
 */
class MutiRadixTreeRandomizedTest {

    private static final String ALPHABET = "abc";
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
        WriteAbleStandardRadixTree<Integer> tree = new WriteAbleStandardRadixTree<>();
        Map<String, Integer> reference = new HashMap<>();
        for (int op = 0; op < OPERATIONS_PER_ROUND; op++) {
            String key = randomKey(random, 1, 5);
            if (random.nextInt(100) < 55) {
                int value = random.nextInt();
                Object expected = reference.put(key, value);
                assertEquals(expected, tree.put(key, value), seed + " 轮 put 返回值, op=" + op);
            } else {
                boolean expected = reference.remove(key) != null;
                assertEquals(expected, tree.remove(key), seed + " 轮 remove 返回值, op=" + op);
            }
            Object expectedGet = reference.get(key);
            assertEquals(expectedGet, tree.get(key), seed + " 轮 get 操作键, op=" + op);
            assertEquals(reference.containsKey(key), tree.exist(key), seed + " 轮 exist 操作键, op=" + op);
            for (int probe = 0; probe < PROBES_PER_OPERATION; probe++) {
                String probeKey = randomKey(random, 0, 6);
                Object expectedProbeGet = reference.get(probeKey);
                assertEquals(expectedProbeGet, tree.get(probeKey), seed + " 轮 get 探针, op=" + op);
                assertEquals(reference.containsKey(probeKey), tree.exist(probeKey), seed + " 轮 exist 探针, op=" + op);
                assertEquals(referenceMatch(reference, probeKey), tree.match(probeKey), seed + " 轮 match 探针, op=" + op);
            }
        }
    }

    private String randomKey(Random random, int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder key = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            key.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return key.toString();
    }

    private Set<Integer> referenceMatch(Map<String, Integer> reference, String probe) {
        Set<Integer> hits = new HashSet<>();
        for (String key : reference.keySet()) {
            if (probe.startsWith(key)) {
                hits.add(reference.get(key));
            }
        }
        return hits;
    }
}
