package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestUrlTrie} 的匹配语义：固定段优先、参数类型过滤、{@code path} 贪婪吞段、回溯。
 */
class RestUrlTrieMatchTest {

    private static RestUrlTrie<String> trie() {
        return new RestUrlTrie<>();
    }

    @Test
    void staticPathMatchesValueWithEmptyParams() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/b", "v");
        RestUrlTrie.MatchResult<String> hit = t.match("/a/b");
        assertNotNull(hit);
        assertEquals("v", hit.value);
        assertTrue(hit.params.isEmpty());
    }

    @Test
    void matchToleratesRedundantTrailingSlashes() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/b", "v");
        assertEquals("v", t.match("/a/b/").value);
    }

    @Test
    void unmatchablePathReturnsNull() {
        RestUrlTrie<String> t = trie();
        assertNull(t.match("/a"), "空 trie 在 root 层就找不到子节点");
        t.insert("/a/b", "v");
        assertNull(t.match("/a"));
        assertNull(t.match("/x/y"), "完全不相关的路径");
    }

    @Test
    void fixedSegmentWinsOverParameterSegment() {
        RestUrlTrie<String> t = trie();
        t.insert("/x/{a}", "V-param");
        t.insert("/x/specific", "V-fixed");
        RestUrlTrie.MatchResult<String> hit = t.match("/x/specific");
        assertEquals("V-fixed", hit.value, "doMatch 先查固定段，参数节点只是候选");
        assertTrue(hit.params.isEmpty(), "走固定分支时不得把参数名塞进 params");
    }

    @Test
    void typeFilteringMakesCandidateSelectionDeterministic() {
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V-int");
        t.insert("/seg/{b:date}/other", "V-date");

        assertEquals("V-int", t.match("/seg/1/end").value);
        assertEquals("1", t.match("/seg/1/end").params.get("a"));
        assertEquals("V-date", t.match("/seg/2026-10-06/other").value);
        assertEquals("2026-10-06", t.match("/seg/2026-10-06/other").params.get("b"));
        // 交叉：类型过滤后每层只剩一个候选，走不通就是 null（与顺序无关）
        assertNull(t.match("/seg/1/other"));
        assertNull(t.match("/seg/2026-10-06/end"));
    }

    @Test
    void pathParamConsumesAllRemainingSegments() {
        RestUrlTrie<String> t = trie();
        t.insert("/files/{p:path}", "V");
        RestUrlTrie.MatchResult<String> hit = t.match("/files/a/b/c");
        assertEquals("V", hit.value);
        assertEquals("a/b/c", hit.params.get("p"));
    }

    @Test
    void pathParamNeedsAtLeastOneSegment() {
        RestUrlTrie<String> t = trie();
        t.insert("/files/{p:path}", "V");
        assertNull(t.match("/files"), "path 参数至少要吞一段，空尾巴不算命中");
    }

    @Test
    void intTypeAcceptsSignedIntegerOnly() {
        RestUrlTrie<String> t = trie();
        t.insert("/n/{v:int}", "V");
        assertNotNull(t.match("/n/-5"));
        assertNotNull(t.match("/n/42"));
        assertNull(t.match("/n/5.0"), "小数不是 int");
        assertNull(t.match("/n/abc"));
    }

    @Test
    void numTypeAcceptsDecimals() {
        RestUrlTrie<String> t = trie();
        t.insert("/n/{v:num}", "V");
        assertNotNull(t.match("/n/5.0"));
        assertNotNull(t.match("/n/-2.5"));
        assertNotNull(t.match("/n/7"));
        assertNull(t.match("/n/5."));
        assertNull(t.match("/n/abc"));
    }

    @Test
    void boolTypeIsCaseInsensitiveAndWordOnly() {
        RestUrlTrie<String> t = trie();
        t.insert("/n/{v:bool}", "V");
        assertNotNull(t.match("/n/TRUE"));
        assertNotNull(t.match("/n/false"));
        assertNull(t.match("/n/1"), "0/1 不算布尔");
    }

    @Test
    void dateTypeRequiresPaddedIsoDate() {
        RestUrlTrie<String> t = trie();
        t.insert("/n/{v:date}", "V");
        assertNotNull(t.match("/n/2026-10-06"));
        assertNull(t.match("/n/2026-1-1"), "月/日必须补零");
        assertNull(t.match("/n/2026-13-01"));
    }

    @Test
    void ipv4TypeRequiresOctetsInRange() {
        RestUrlTrie<String> t = trie();
        t.insert("/n/{v:ipv4}", "V");
        assertNotNull(t.match("/n/192.168.1.1"));
        assertNotNull(t.match("/n/0.0.0.0"));
        assertNull(t.match("/n/256.1.1.1"));
        assertNull(t.match("/n/abc"));
    }

    @Test
    void regexParamMatchesCustomPattern() {
        RestUrlTrie<String> t = trie();
        t.insert("/x/{p:regex=^[a-c]+$}", "V");
        assertEquals("abc", t.match("/x/abc").params.get("p"));
        assertNull(t.match("/x/xyz"));
    }

    @Test
    void paramsMapIsUnmodifiable() {
        RestUrlTrie<String> t = trie();
        t.insert("/a/{id}", "V");
        RestUrlTrie.MatchResult<String> hit = t.match("/a/9");
        assertThrows(UnsupportedOperationException.class, () -> hit.params.put("k", "v"));
    }

    @Test
    @DisplayName("同层多候选：首选即正解时正常返回，压栈的备选只是被丢弃")
    void multipleCandidatesPushFrameButFirstOneWins() {
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V");
        t.insert("/seg/{b:str}/end", "V");
        // 两个参数节点都能吞 "1"，doMatch 会为非首选者压一帧（:369-370）。
        // 无论 HashMap 把哪个排在首位，两条分支都指向值为 "V" 的 end 节点，
        // 所以结果与顺序无关；参数表里恰好只有一个名字被填上。
        RestUrlTrie.MatchResult<String> hit = t.match("/seg/1/end");
        assertNotNull(hit, "多候选中首选走通，不应回溯");
        assertEquals("V", hit.value);
        assertEquals(1, hit.params.size(), "只有被选中的那个参数节点会写入 params");
    }

    @Test
    @DisplayName("固定段命中时，同层参数候选只被记账，不参与结果")
    void fixedMatchRecordsParamCandidatesWithoutUsingThem() {
        RestUrlTrie<String> t = trie();
        t.insert("/m/{a:str}/v", "V");
        t.insert("/m/{b:str}/v", "V");
        t.insert("/m/keep/v", "V");
        // "keep" 有固定节点，参数分支被收集且（因数量≥2）压栈（:322-328），但走的是固定段，
        // params 保持为空——这正是 fixedSegmentWinsOverParameterSegment 的多候选版本。
        RestUrlTrie.MatchResult<String> hit = t.match("/m/keep/v");
        assertEquals("V", hit.value);
        assertTrue(hit.params.isEmpty(), "固定段命中时不得写入任何参数");
    }

    @Test
    @DisplayName("钉住 F13 的可终止半边：单候选死路能干净返回 null")
    void singleCandidateDeadEndReturnsNull() {
        // 钉住 F13：若将来修正，本用例应改为期望正确行为
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V-int");
        assertNull(t.match("/seg/1/other"), "候选只有一个，压栈从未发生，栈空即返回 null");
        // 同层有 ≥2 候选且首选走不通的形状不能进常驻用例：三个出栈恢复点
        // （:297-302 / :339-344 / :358-363）都只还原 node/index/params，从不消费
        // Frame.iterator，于是恢复点重新收集候选、再次选中同一个 first，恒定自旋。
        // 实测（本任务执行期）插入 "/seg/{b:str}/other" 后 match("/seg/1/other")
        // 30 秒 JUnit 超时，栈顶停在 RestUrlTrie.java:354 的重收集循环。
        // 该路径经 DefaultRouterContainer.trieMatch:57 就在请求热路径上（见缺陷清单 F13）。
    }

    @Test
    @DisplayName("钉住 F14：非法正则能在插入期通过，到匹配时才抛 PatternSyntaxException")
    void malformedRegexInsertsFineAndBlowsUpAtMatch() {
        // 钉住 F14：若将来修正，本用例应改为期望正确行为
        RestUrlTrie<String> t = trie();
        t.insert("/x/{p:regex=[}", "V");   // 插入期不编译正则，所以这一步是成功的
        assertThrows(PatternSyntaxException.class, () -> t.match("/x/abc"));
    }
}
