package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link StatusLine} 的契约：常量与注册表为标准状态码提供共享实例，实例只能经
 * {@code of} 创建，已收录的状态码一律复用共享实例，未收录的状态码建立新实例且不抛错，
 * 构造期校验状态码非负与描述短语不含换行回车。
 */
class StatusLineTest {

    // ---------- 查询与共享 ----------

    @Test
    void ofKnownCodeReturnsSharedInstance() {
        assertSame(StatusLine.NOT_FOUND, StatusLine.of(404), "已收录状态码经注册表返回同一共享实例");
        assertEquals("Not Found", StatusLine.NOT_FOUND.reason(), "共享实例携带标准描述短语");
    }

    @Test
    void ofKnownCodeIgnoresSpecifiedReason() {
        assertSame(StatusLine.NOT_FOUND, StatusLine.of(404, "custom"),
                "已收录状态码即使指定描述短语也返回共享实例");
    }

    @Test
    void ofUnknownCodeReturnsNewInstanceWithoutThrowing() {
        StatusLine first = StatusLine.of(599);
        StatusLine second = StatusLine.of(599);

        assertEquals(599, first.code());
        assertEquals("Unknown Status(599)", first.reason(), "未收录状态码的短语记为 Unknown Status");
        assertNotSame(first, second, "未收录状态码每次返回新实例，不进注册表");
    }

    @Test
    void ofUnknownCodeAdoptsSpecifiedReason() {
        StatusLine statusLine = StatusLine.of(599, "custom");

        assertEquals(599, statusLine.code());
        assertEquals("custom", statusLine.reason(), "未收录状态码采用指定描述短语");
    }

    @Test
    void zeroCodeIsLegalUnknown() {
        StatusLine statusLine = StatusLine.of(0);

        assertEquals(0, statusLine.code(), "状态码零合法，不抛错");
        assertEquals("Unknown Status(0)", statusLine.reason());
    }

    @Test
    void constantsCarryStandardPhrasesPerClass() {
        assertEquals(100, StatusLine.CONTINUE.code());
        assertEquals("Continue", StatusLine.CONTINUE.reason());
        assertEquals(200, StatusLine.OK.code());
        assertEquals("OK", StatusLine.OK.reason());
        assertEquals(301, StatusLine.MOVED_PERMANENTLY.code());
        assertEquals("Moved Permanently", StatusLine.MOVED_PERMANENTLY.reason());
        assertEquals(404, StatusLine.NOT_FOUND.code());
        assertEquals("Not Found", StatusLine.NOT_FOUND.reason());
        assertEquals(503, StatusLine.SERVICE_UNAVAILABLE.code());
        assertEquals("Service Unavailable", StatusLine.SERVICE_UNAVAILABLE.reason());
    }

    // ---------- 判等 ----------

    @Test
    void equalsAndHashCodeFollowCodeAndReason() {
        assertEquals(StatusLine.of(599), StatusLine.of(599), "同码同短语按值判等");
        assertEquals(StatusLine.of(599).hashCode(), StatusLine.of(599).hashCode(), "哈希与判等口径一致");
        assertNotSame(StatusLine.of(599), StatusLine.of(599, "custom"), "同码不同短语不相等");
    }

    // ---------- 构造期校验 ----------

    @Test
    void negativeCodeIsRejected() {
        assertEquals("code must be positive or zero",
                assertThrows(IllegalArgumentException.class, () -> StatusLine.of(-1)).getMessage());
        assertEquals("code must be positive or zero",
                assertThrows(IllegalArgumentException.class, () -> StatusLine.of(-1, "x")).getMessage());
    }

    @Test
    void nullReasonIsRejected() {
        assertEquals("reason must not be null",
                assertThrows(NullPointerException.class, () -> StatusLine.of(599, null)).getMessage());
    }

    @Test
    void reasonWithCarriageReturnOrLineFeedIsRejected() {
        IllegalArgumentException cr = assertThrows(IllegalArgumentException.class,
                () -> StatusLine.of(599, "bad\rreason"));
        assertEquals("reason contains one of the following prohibited characters: \\r\\n: bad\rreason",
                cr.getMessage());
        IllegalArgumentException lf = assertThrows(IllegalArgumentException.class,
                () -> StatusLine.of(599, "bad\nreason"));
        assertEquals("reason contains one of the following prohibited characters: \\r\\n: bad\nreason",
                lf.getMessage());
    }
}
