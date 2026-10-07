package top.turboweb.http.middleware.router.info.autobind;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TypeConverters} 的源类型覆盖面与转换语义（spec §5：autobind 解析器）。
 *
 * <p>只断言"哪一类有转换器"和"转换出来是什么"，不碰解析器。
 */
class TypeConvertersTest {

    /** 取转换器并立即转换；缺失转换器视为夹具错误直接红。 */
    private static Object convert(Class<?> type, String value) {
        TypeConverters.Converter converter = TypeConverters.getConverter(type);
        assertNotNull(converter, () -> "缺少转换器: " + type);
        return converter.convert(value);
    }

    @Test
    void everyDeclaredTypeHasAConverter() {
        Class<?>[] supported = {
                String.class, Integer.class, int.class, Long.class, long.class,
                Double.class, double.class, Boolean.class, boolean.class,
                LocalDate.class, LocalDateTime.class, LocalTime.class
        };
        for (Class<?> type : supported) {
            assertNotNull(TypeConverters.getConverter(type), () -> "未注册: " + type);
        }
    }

    @Test
    void unsupportedTypesHaveNoConverter() {
        assertNull(TypeConverters.getConverter(Map.class));
        assertNull(TypeConverters.getConverter(Object.class));
        assertNull(TypeConverters.getConverter(float.class), "Float 未在表内");
    }

    @Test
    void stringIsIdentity() {
        assertEquals("x", convert(String.class, "x"));
        assertNull(convert(String.class, null), "String 转换器不做 null 兜底，原样返回 null");
    }

    @Test
    void boxedNumbersPreserveNullButPrimitivesDefaultToZero() {
        assertEquals(42, convert(Integer.class, "42"));
        assertNull(convert(Integer.class, null));
        assertEquals(0, convert(int.class, null));

        assertEquals(42L, convert(Long.class, "42"));
        assertNull(convert(Long.class, null));
        assertEquals(0L, convert(long.class, null));

        assertEquals(1.5d, convert(Double.class, "1.5"));
        assertNull(convert(Double.class, null));
        assertEquals(0.0d, convert(double.class, null));
    }

    @Test
    void booleansUseParseBooleanRules() {
        assertEquals(Boolean.TRUE, convert(Boolean.class, "true"));
        assertEquals(Boolean.TRUE, convert(Boolean.class, "TRUE"), "Boolean.parseBoolean 忽略大小写");
        assertEquals(Boolean.FALSE, convert(Boolean.class, "yes"), "非 \"true\" 一律 false，不抛错");
        assertNull(convert(Boolean.class, null));
        assertEquals(Boolean.FALSE, convert(boolean.class, null));
    }

    @Test
    void temporalTypesParseIsoFormats() {
        assertEquals(LocalDate.of(2026, 10, 6), convert(LocalDate.class, "2026-10-06"));
        assertEquals(LocalDateTime.of(2026, 10, 6, 10, 20, 30),
                convert(LocalDateTime.class, "2026-10-06T10:20:30"));
        assertEquals(LocalTime.of(10, 20, 30), convert(LocalTime.class, "10:20:30"));
        assertNull(convert(LocalDate.class, null));
    }

    @Test
    void malformedValuesPropagateParseExceptions() {
        assertThrows(NumberFormatException.class, () -> convert(Integer.class, "abc"));
        assertThrows(DateTimeParseException.class, () -> convert(LocalDate.class, "not-a-date"));
    }
}
