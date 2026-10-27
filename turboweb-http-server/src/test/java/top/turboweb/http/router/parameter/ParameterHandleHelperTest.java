package top.turboweb.http.router.parameter;

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
 * {@link ParameterHandleHelper} 的源类型覆盖面与转换语义（spec §5：autobind 解析器）。
 *
 * <p>只断言"哪一类有处理器"和"处理出来是什么"，不碰解析器。
 */
class ParameterHandleHelperTest {

    /** 取处理器并立即处理；缺失处理器视为夹具错误直接红。 */
    private static Object handle(Class<?> type, String value) {
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        assertNotNull(simpleTypeHandler, () -> "缺少处理器: " + type);
        return simpleTypeHandler.handle(value);
    }

    @Test
    void everyDeclaredTypeHasASimpleTypeHandler() {
        Class<?>[] supported = {
                String.class, Integer.class, int.class, Long.class, long.class,
                Double.class, double.class, Boolean.class, boolean.class,
                LocalDate.class, LocalDateTime.class, LocalTime.class
        };
        for (Class<?> type : supported) {
            assertNotNull(ParameterHandleHelper.getSimpleTypeHandler(type), () -> "未注册: " + type);
        }
    }

    @Test
    void unsupportedTypesHaveNoSimpleTypeHandler() {
        assertNull(ParameterHandleHelper.getSimpleTypeHandler(Map.class));
        assertNull(ParameterHandleHelper.getSimpleTypeHandler(Object.class));
        assertNull(ParameterHandleHelper.getSimpleTypeHandler(float.class), "Float 未在表内");
    }

    @Test
    void stringIsIdentity() {
        assertEquals("x", handle(String.class, "x"));
        assertNull(handle(String.class, null), "String 处理器不做 null 兜底，原样返回 null");
    }

    @Test
    void boxedNumbersPreserveNullButPrimitivesDefaultToZero() {
        assertEquals(42, handle(Integer.class, "42"));
        assertNull(handle(Integer.class, null));
        assertEquals(0, handle(int.class, null));

        assertEquals(42L, handle(Long.class, "42"));
        assertNull(handle(Long.class, null));
        assertEquals(0L, handle(long.class, null));

        assertEquals(1.5d, handle(Double.class, "1.5"));
        assertNull(handle(Double.class, null));
        assertEquals(0.0d, handle(double.class, null));
    }

    @Test
    void booleansUseParseBooleanRules() {
        assertEquals(Boolean.TRUE, handle(Boolean.class, "true"));
        assertEquals(Boolean.TRUE, handle(Boolean.class, "TRUE"), "Boolean.parseBoolean 忽略大小写");
        assertEquals(Boolean.FALSE, handle(Boolean.class, "yes"), "非 \"true\" 一律 false，不抛错");
        assertNull(handle(Boolean.class, null));
        assertEquals(Boolean.FALSE, handle(boolean.class, null));
    }

    @Test
    void temporalTypesParseIsoFormats() {
        assertEquals(LocalDate.of(2026, 10, 6), handle(LocalDate.class, "2026-10-06"));
        assertEquals(LocalDateTime.of(2026, 10, 6, 10, 20, 30),
                handle(LocalDateTime.class, "2026-10-06T10:20:30"));
        assertEquals(LocalTime.of(10, 20, 30), handle(LocalTime.class, "10:20:30"));
        assertNull(handle(LocalDate.class, null));
    }

    @Test
    void malformedValuesPropagateParseExceptions() {
        assertThrows(NumberFormatException.class, () -> handle(Integer.class, "abc"));
        assertThrows(DateTimeParseException.class, () -> handle(LocalDate.class, "not-a-date"));
    }
}
