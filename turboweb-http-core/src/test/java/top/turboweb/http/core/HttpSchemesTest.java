package top.turboweb.http.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpSchemesTest {

    @Test
    @DisplayName("静态常量 Http 与 Https 持有所对应 scheme 字符串")
    void constantsHoldSchemeValues() {
        assertEquals("http", HttpSchemes.Http.value());
        assertEquals("https", HttpSchemes.Https.value());
    }

    @Test
    @DisplayName("两个静态常量是互不相同的实例")
    void constantsAreDistinctInstances() {
        assertNotSame(HttpSchemes.Http, HttpSchemes.Https);
    }

    @Test
    @DisplayName("of 按字符串取得既有常量，不新建实例")
    void ofReturnsExistingConstant() {
        assertSame(HttpSchemes.Http, HttpSchemes.of("http"));
        assertSame(HttpSchemes.Https, HttpSchemes.of("https"));
    }

    @Test
    @DisplayName("of 传入非 http 或 https 时拒绝")
    void ofRejectsUnknownScheme() {
        assertThrows(IllegalArgumentException.class, () -> HttpSchemes.of("ftp"));
        assertThrows(IllegalArgumentException.class, () -> HttpSchemes.of(""));
        assertThrows(IllegalArgumentException.class, () -> HttpSchemes.of("HTTP"));
    }

    @Test
    @DisplayName("of 传入 null 时拒绝")
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> HttpSchemes.of(null));
    }
}