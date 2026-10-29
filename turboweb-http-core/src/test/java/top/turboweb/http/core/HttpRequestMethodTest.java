package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpRequestMethod}：枚举集合覆盖标准方法全集，valueOf 按请求行方法字面量
 * 原文转化（大小写敏感），扩展方法名与非法字面量拒绝。
 */
class HttpRequestMethodTest {

    @Test
    void coversAllStandardMethods() {
        assertEquals(9, HttpRequestMethod.values().length);
    }

    @Test
    void valueOfParsesEveryStandardMethod() {
        assertSame(HttpRequestMethod.GET, HttpRequestMethod.valueOf("GET"));
        assertSame(HttpRequestMethod.HEAD, HttpRequestMethod.valueOf("HEAD"));
        assertSame(HttpRequestMethod.POST, HttpRequestMethod.valueOf("POST"));
        assertSame(HttpRequestMethod.PUT, HttpRequestMethod.valueOf("PUT"));
        assertSame(HttpRequestMethod.PATCH, HttpRequestMethod.valueOf("PATCH"));
        assertSame(HttpRequestMethod.DELETE, HttpRequestMethod.valueOf("DELETE"));
        assertSame(HttpRequestMethod.OPTIONS, HttpRequestMethod.valueOf("OPTIONS"));
        assertSame(HttpRequestMethod.TRACE, HttpRequestMethod.valueOf("TRACE"));
        assertSame(HttpRequestMethod.CONNECT, HttpRequestMethod.valueOf("CONNECT"));
    }

    @Test
    void wireNameEqualsConstantName() {
        for (HttpRequestMethod method : HttpRequestMethod.values()) {
            assertSame(method, HttpRequestMethod.valueOf(method.name()));
            assertEquals(method.name(), method.toString());
        }
    }

    @Test
    void valueOfIsCaseSensitive() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequestMethod.valueOf("get"));
    }

    @Test
    void valueOfRejectsExtensionMethod() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequestMethod.valueOf("PROPFIND"));
    }

    @Test
    void valueOfRejectsNull() {
        assertThrows(NullPointerException.class, () -> HttpRequestMethod.valueOf(null));
    }
}
