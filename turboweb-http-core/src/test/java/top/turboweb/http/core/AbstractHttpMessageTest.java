package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractHttpMessage} 的头部操作全量契约：自有存储上的读写、
 * 头名大小写不敏感、头名首现原文记录、取值按出现顺序保持。
 */
class AbstractHttpMessageTest {

    /** 头部操作全量由抽象类实现，测试用空实现子类承载 */
    private static final class TestMessage extends AbstractHttpMessage<String, String> {
    }

    private static TestMessage message() {
        return new TestMessage();
    }

    // ---------- 读取 ----------

    @Test
    void headerReturnsFirstValueAndNullWhenMissing() {
        TestMessage message = message();
        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");

        assertEquals("a", message.header("X-Multi"), "单值访问器取首值");
        assertNull(message.header("X-Missing"), "缺头返回 null 而非抛错");
    }

    @Test
    void headerLookupIgnoresNameCase() {
        TestMessage message = message();
        message.setHeader("X-Custom", "v");

        assertEquals("v", message.header("x-custom"), "头名查询大小写不敏感");
        assertTrue(message.containsHeader("X-CUSTOM"));
        assertFalse(message.containsHeader("X-Other"));
    }

    @Test
    void headerValuesPreservesOrderAndEmptyWhenMissing() {
        TestMessage message = message();
        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");

        assertEquals(List.of("a", "b"), message.headerValues("X-Multi"), "同名取值按出现顺序保持");
        assertEquals(List.of(), message.headerValues("X-None"), "缺头返回空列表");
    }

    @Test
    void headerValuesViewIsUnmodifiable() {
        TestMessage message = message();
        message.setHeader("X-One", "v");

        List<String> values = message.headerValues("X-One");

        assertThrows(UnsupportedOperationException.class, () -> values.add("other"),
                "取值列表为只读视图，写入须走消息的写通道");
    }

    @Test
    void headerNamesKeepOriginalCaseAndMergeCaseVariants() {
        TestMessage message = message();
        message.addHeader("X-Multi", "a");
        message.addHeader("x-multi", "b");
        message.setHeader("X-Other", "v");

        Set<String> names = message.headerNames();

        assertTrue(names.contains("X-Multi"), "同名合并后头名保持首现原文");
        assertFalse(names.contains("x-multi"), "变体写法不再单列");
        assertTrue(names.contains("X-Other"));
    }

    @Test
    void headerNamesReturnSnapshot() {
        TestMessage message = message();
        message.setHeader("X-One", "v");
        Set<String> names = message.headerNames();

        message.setHeader("X-Late", "w");

        assertFalse(names.contains("X-Late"), "头名为返回时刻的快照，不随后续写入联动");
        assertTrue(message.headerNames().contains("X-Late"));
    }

    @Test
    void headerSizeCountsEntriesPerValue() {
        TestMessage message = message();
        assertTrue(message.headerIsEmpty(), "无写入时记为无头");
        assertEquals(0, message.headerSize());
        assertEquals(Set.of(), message.headerNames());

        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");
        message.setHeader("Host", "localhost");
        assertEquals(3, message.headerSize(), "同名多值按条目计数");
        assertFalse(message.headerIsEmpty());
    }

    // ---------- 写入与修改 ----------

    @Test
    void setHeaderOverridesAllSameNameValues() {
        TestMessage message = message();
        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");

        message.setHeader("X-Multi", "c");

        assertEquals(List.of("c"), message.headerValues("X-Multi"), "覆盖同名全部既有取值");
        assertEquals(1, message.headerSize());
    }

    @Test
    void setHeaderStoresGivenNameCase() {
        TestMessage message = message();
        message.setHeader("x-a", "1");

        message.setHeader("X-A", "2");

        assertTrue(message.headerNames().contains("X-A"), "覆盖后头名按写入原文记录");
        assertFalse(message.headerNames().contains("x-a"));
    }

    @Test
    void addHeaderAppendsToExistingEntry() {
        TestMessage message = message();
        message.setHeader("X-Multi", "a");

        message.addHeader("X-Multi", "b");

        assertEquals(List.of("a", "b"), message.headerValues("X-Multi"), "追加并入既有条目");
    }

    @Test
    void removeHeaderDeletesAllValuesCaseInsensitively() {
        TestMessage message = message();
        message.addHeader("X-Multi", "a");
        message.addHeader("x-multi", "b");

        message.removeHeader("X-MULTI");

        assertFalse(message.containsHeader("X-Multi"), "删除按头名大小写不敏感");
        assertTrue(message.headerIsEmpty());
    }

    @Test
    void removeHeaderIsNoOpWhenMissing() {
        TestMessage message = message();

        message.removeHeader("X-Missing");

        assertTrue(message.headerIsEmpty(), "缺头删除无操作不抛错");
    }

    @Test
    void clearHeadersEmptiesAll() {
        TestMessage message = message();
        message.setHeader("Host", "localhost");
        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");

        message.clearHeaders();

        assertTrue(message.headerIsEmpty(), "清空后记为无头");
        assertEquals(0, message.headerSize());
        assertEquals(Set.of(), message.headerNames());
    }

    @Test
    void nullNameOrValueRejectedOnWrite() {
        TestMessage message = message();

        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> message.setHeader(null, "v")).getMessage());
        assertEquals("value must not be null",
                assertThrows(NullPointerException.class, () -> message.setHeader("X-One", null)).getMessage());
        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> message.addHeader(null, "v")).getMessage());
        assertEquals("value must not be null",
                assertThrows(NullPointerException.class, () -> message.addHeader("X-One", null)).getMessage());
        assertEquals("name must not be null",
                assertThrows(NullPointerException.class, () -> message.removeHeader(null)).getMessage());
    }

    // ---------- 与消息契约 default 方法的衔接 ----------

    @Test
    void contentLengthReadsDeclaredHeader() {
        TestMessage message = message();
        message.setHeader("Content-Length", " 42 ");

        assertEquals(42L, message.contentLength(), "Content-Length 兜底口径经自有存储生效");
    }
}
