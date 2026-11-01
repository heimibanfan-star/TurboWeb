package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Headers} 的契约：自有存储上的读写、头名大小写不敏感、
 * 头名首现原文记录、同名多值按出现顺序保持、只读视图与快照语义。
 */
class HeadersTest {

    // ---------- 读取 ----------

    @Test
    void getReturnsFirstValueAndNullWhenMissing() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.add("X-Multi", "b");

        assertEquals("a", headers.get("X-Multi"), "单值访问器取首值");
        assertNull(headers.get("X-Missing"), "缺头返回 null 而非抛错");
    }

    @Test
    void lookupIgnoresNameCase() {
        Headers headers = new Headers();
        headers.set("X-Custom", "v");

        assertEquals("v", headers.get("x-custom"), "头名查询大小写不敏感");
        assertTrue(headers.contains("X-CUSTOM"));
        assertFalse(headers.contains("X-Other"));
    }

    @Test
    void valuesPreservesOrderAndEmptyWhenMissing() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.add("X-Multi", "b");

        assertEquals(List.of("a", "b"), headers.values("X-Multi"), "同名取值按出现顺序保持");
        assertEquals(List.of(), headers.values("X-None"), "缺头返回空列表");
    }

    @Test
    void valuesViewIsUnmodifiable() {
        Headers headers = new Headers();
        headers.set("X-One", "v");

        List<String> values = headers.values("X-One");

        assertThrows(UnsupportedOperationException.class, () -> values.add("other"),
                "取值列表为只读视图，写入须走 Headers 的写通道");
    }

    @Test
    void namesKeepOriginalCaseAndMergeCaseVariants() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.add("x-multi", "b");
        headers.set("X-Other", "v");

        Set<String> names = headers.names();

        assertTrue(names.contains("X-Multi"), "同名合并后头名保持首现原文");
        assertFalse(names.contains("x-multi"), "变体写法不再单列");
        assertTrue(names.contains("X-Other"));
    }

    @Test
    void setRecordsWrittenOriginalName() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.set("x-multi", "only");

        assertEquals(List.of("only"), headers.values("x-multi"), "覆盖式写入替换全部既有取值");
        assertTrue(headers.names().contains("x-multi"), "覆盖式写入后头名改按写入原文记录");
        assertFalse(headers.names().contains("X-Multi"));
    }

    // ---------- 键值对视图 ----------

    @Test
    void asMapCarriesValuesAndSkipsEmptyEntries() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.add("X-Multi", "b");
        headers.set("X-One", "v");

        Map<String, List<String>> map = headers.asMap();

        assertEquals(List.of("a", "b"), map.get("X-Multi"));
        assertEquals(List.of("v"), map.get("X-One"));
        assertEquals(2, map.size(), "键值对视图按头名去重计数");
    }

    @Test
    void asMapViewIsSnapshot() {
        Headers headers = new Headers();
        headers.set("X-One", "v");
        Map<String, List<String>> map = headers.asMap();

        headers.set("X-Late", "w");

        assertFalse(map.containsKey("X-Late"), "键值对视图为快照，后续写入不可见");
        assertEquals(2, headers.asMap().size(), "补写后的新快照含全部条目");
    }

    @Test
    void asMapRejectsMutation() {
        Headers headers = new Headers();
        headers.set("X-One", "v");

        Map<String, List<String>> map = headers.asMap();

        assertThrows(UnsupportedOperationException.class, () -> map.put("X-New", List.of("x")),
                "键值对视图不可写入，写通道在 Headers 自身");
        assertThrows(UnsupportedOperationException.class, () -> map.get("X-One").add("other"),
                "视图内取值列表同样只读");
    }

    // ---------- 计数与判空 ----------

    @Test
    void sizeCountsEntriesPerValue() {
        Headers headers = new Headers();
        assertTrue(headers.isEmpty(), "新建 Headers 无头");

        headers.add("X-Multi", "a");
        headers.add("X-Multi", "b");
        headers.set("X-One", "v");

        assertEquals(3, headers.size(), "同名多值按条目分别计数");
        assertFalse(headers.isEmpty());
    }

    // ---------- 写入与删除 ----------

    @Test
    void removeDeletesAllValuesCaseInsensitively() {
        Headers headers = new Headers();
        headers.add("X-Multi", "a");
        headers.add("X-Multi", "b");

        headers.remove("x-MULTI");

        assertTrue(headers.isEmpty(), "删除按头名整组移除");
        assertTrue(headers.names().isEmpty());
    }

    @Test
    void removeMissingHeaderIsNoOp() {
        Headers headers = new Headers();

        headers.remove("X-None");

        assertTrue(headers.isEmpty(), "缺头删除无操作不抛错");
    }

    @Test
    void clearRemovesEverything() {
        Headers headers = new Headers();
        headers.set("X-One", "v");
        headers.set("X-Two", "w");

        headers.clear();

        assertTrue(headers.isEmpty());
        assertEquals(List.of(), headers.values("X-One"));
    }

    @Test
    void nullNameOrValueRejectedOnWrite() {
        Headers headers = new Headers();

        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> headers.set(null, "v")).getMessage());
        assertEquals("value must not be null",
                assertThrows(NullPointerException.class, () -> headers.set("X-One", null)).getMessage());
        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> headers.add(null, "v")).getMessage());
        assertEquals("value must not be null",
                assertThrows(NullPointerException.class, () -> headers.add("X-One", null)).getMessage());
        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> headers.remove(null)).getMessage());
    }

    // ---------- Content-Length 兜底口径 ----------

    @Test
    void contentLengthParsesDeclaredValueIgnoringSurroundingWhitespace() {
        Headers headers = new Headers();
        headers.set("Content-Length", " 42 ");

        assertEquals(42L, headers.contentLength(), "取值按声明解析并容忍首尾空白");
    }

    @Test
    void contentLengthDefaultsToZeroWhenMissingInvalidOrNegative() {
        Headers headers = new Headers();
        assertEquals(0L, headers.contentLength(), "缺头记 0");

        headers.set("Content-Length", "not-a-number");
        assertEquals(0L, headers.contentLength(), "非数值记 0");

        headers.set("Content-Length", "-5");
        assertEquals(0L, headers.contentLength(), "负数记 0");
    }

    // ---------- 拷贝 ----------

    @Test
    void copyCarriesAllEntriesIndependently() {
        Headers source = new Headers();
        source.add("X-Multi", "a");
        source.add("X-Multi", "b");
        source.set("X-One", "v");

        Headers copy = new Headers(source);

        assertEquals(source.asMap(), copy.asMap(), "拷贝携带全部头条目");
        copy.set("X-Multi", "changed");
        copy.set("X-New", "added");

        assertEquals(List.of("a", "b"), source.values("X-Multi"), "拷贝写入不影响源");
        assertFalse(source.contains("X-New"), "拷贝新增不落入源");
        assertNotSame(source.values("X-Multi"), copy.values("X-Multi"),
                "拷贝的取值序列与源不共享同一实例");
    }
}
