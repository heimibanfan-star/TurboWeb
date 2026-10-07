package top.turboweb.commons.struct.trie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestUrlTrie} 的匹配语义：固定段优先、参数类型过滤、{@code path} 贪婪吞段、死路即返回 null（F13 修法 A 后已无回溯栈）。
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
    @DisplayName("同层多候选：首选走通即正常返回（F13 修法 A 后其余候选就地丢弃，不再压栈）")
    void multipleCandidatesFirstOneWinsWithoutBacktrack() {
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V");
        t.insert("/seg/{b:str}/end", "V");
        // 两个参数节点都能吞 "1"。无论 HashMap 把哪个排在首位，两条分支都指向值为 "V"
        // 的 end 节点，所以结果与顺序无关；参数表里恰好只有一个名字被填上。
        RestUrlTrie.MatchResult<String> hit = t.match("/seg/1/end");
        assertNotNull(hit, "首选走通应正常返回");
        assertEquals("V", hit.value);
        assertEquals(1, hit.params.size(), "只有被选中的那个参数节点会写入 params");
    }

    @Test
    @DisplayName("固定段命中时，同层参数候选不参与结果（F13 修法 A 后也不再为它们压栈记账）")
    void fixedMatchWinsOverParamCandidates() {
        RestUrlTrie<String> t = trie();
        t.insert("/m/{a:str}/v", "V");
        t.insert("/m/{b:str}/v", "V");
        t.insert("/m/keep/v", "V");
        // "keep" 有固定节点，走的是固定段，params 保持为空——这正是
        // fixedSegmentWinsOverParameterSegment 的多候选版本。
        RestUrlTrie.MatchResult<String> hit = t.match("/m/keep/v");
        assertEquals("V", hit.value);
        assertTrue(hit.params.isEmpty(), "固定段命中时不得写入任何参数");
    }

    @Test
    @DisplayName("F13（已修·修法 A）：单候选死路干净返回 null")
    void singleCandidateDeadEndReturnsNull() {
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V-int");
        assertNull(t.match("/seg/1/other"), "候选只有一个，走不通即返回 null");
    }

    @Test
    @DisplayName("F13 回归（修法 A）：同层多候选且首选走不通，必须及时返回 null 而非自旋")
    void multipleCandidateDeadEndTerminatesWithNull() throws InterruptedException {
        // 修复前本形状恒定自旋（出栈恢复点从不消费 Frame.iterator，重收集必再选中同一首选）。
        // 若将来有人重新引入"压栈-恢复"而不真正消费候选，本用例超时即红：探测线程设为
        // daemon，回退场景下也不会把 forked worker 拖死。
        RestUrlTrie<String> t = trie();
        t.insert("/seg/{a:int}/end", "V-int");
        t.insert("/seg/{b:str}/other", "V-other");
        assertNullTerminates(t, "/seg/1/dead");
    }

    @Test
    @DisplayName("F13 回归（修法 A）：固定段 + 同层≥2参数候选、固定路走死，同样及时返回 null")
    void fixedBranchDeadEndWithExtraCandidatesTerminatesWithNull() throws InterruptedException {
        // 修复前 :322-328 的记账压栈同样构成自旋入口：固定段死路出栈后回到同一节点同一
        // 段，重收集出同样的候选集，再压栈、再走固定段、再死。
        RestUrlTrie<String> t = trie();
        t.insert("/m/{a:str}/v", "V");
        t.insert("/m/{b:str}/v", "V");
        t.insert("/m/keep/v", "V");
        assertNullTerminates(t, "/m/keep/dead");
    }

    private static void assertNullTerminates(RestUrlTrie<String> t, String path) throws InterruptedException {
        AtomicReference<RestUrlTrie.MatchResult<String>> result = new AtomicReference<>();
        Thread probe = new Thread(() -> result.set(t.match(path)), "f13-termination-probe");
        probe.setDaemon(true);
        probe.start();
        probe.join(5000);
        assertFalse(probe.isAlive(), () -> path + " 5 秒内未返回——F13 自旋回退");
        assertNull(result.get(), () -> path + " 死路应返回 null");
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
