package top.turboweb.rest.uri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URIHelper 查询解析与路径切分、格式化原语的行为契约：
 * 分隔符口径、空白折叠、空名跳过、编码原语的还原与输出。
 */
class URIHelperInternalTest {

    private static ParamPair nvp(String name, String value) {
        return new ParamPair(name, value);
    }

    // ---------- parseQuery ----------

    @Test
    @DisplayName("parseQuery：null 输入返回 null")
    void parseQueryNull() {
        assertNull(URIHelper.parseQuery(null, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parseQuery：空串返回空列表")
    void parseQueryEmpty() {
        assertTrue(URIHelper.parseQuery("", StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    @DisplayName("parseQuery：常规名值对")
    void parseQueryPairs() {
        assertEquals(List.of(nvp("a", "1"), nvp("b", "2")),
                URIHelper.parseQuery("a=1&b=2", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parseQuery：无等号参数值为 null，空值参数值为空串")
    void parseQueryValuePresence() {
        assertEquals(List.of(nvp("a", null), nvp("b", "")),
                URIHelper.parseQuery("a&b=", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parseQuery：空名参数跳过")
    void parseQuerySkipsEmptyNames() {
        assertEquals(List.of(nvp("a", "1")),
                URIHelper.parseQuery("=x&&a=1", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parseQuery：值中的等号保留在值内")
    void parseQueryEqualsInValue() {
        assertEquals(List.of(nvp("a", "1=2")),
                URIHelper.parseQuery("a=1=2", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parseQuery：空白折叠为单个空格")
    void parseQueryCollapsesWhitespace() {
        assertAll(
                () -> assertEquals(List.of(nvp("a", "1 2")),
                        URIHelper.parseQuery("a=1 2", StandardCharsets.UTF_8)),
                () -> assertEquals(List.of(nvp("a", "1 2")),
                        URIHelper.parseQuery("a=1 \t 2", StandardCharsets.UTF_8)),
                () -> assertEquals(List.of(nvp("a b", "c")),
                        URIHelper.parseQuery(" a b = c ", StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("parseQuery：尾随分隔符不产生空名参数")
    void parseQueryTrailingSeparator() {
        assertEquals(List.of(nvp("a", "1")),
                URIHelper.parseQuery("a=1&", StandardCharsets.UTF_8));
    }

    // ---------- splitPath / parsePath ----------

    @Test
    @DisplayName("splitPath：null 与空串返回空列表")
    void splitPathNullOrEmpty() {
        assertTrue(URIHelper.splitPath(null).isEmpty());
        assertTrue(URIHelper.splitPath("").isEmpty());
    }

    @Test
    @DisplayName("splitPath：前导斜杠跳过，连续斜杠产生空段")
    void splitPathSegments() {
        assertAll(
                () -> assertEquals(List.of("a", "b"), URIHelper.splitPath("/a/b")),
                () -> assertEquals(List.of("a", "b"), URIHelper.splitPath("a/b")),
                () -> assertEquals(List.of("a", "", "b"), URIHelper.splitPath("a//b")),
                () -> assertEquals(List.of("a", "b", ""), URIHelper.splitPath("a/b/")),
                () -> assertEquals(List.of(""), URIHelper.splitPath("/")));
    }

    @Test
    @DisplayName("parsePath：切分后逐段解码；null 返回空列表")
    void parsePathDecodes() {
        assertAll(
                () -> assertEquals(List.of("a b"), URIHelper.parsePath("/a%20b", StandardCharsets.UTF_8)),
                () -> assertTrue(URIHelper.parsePath(null, StandardCharsets.UTF_8).isEmpty()));
    }

    // ---------- formatPath / formatQuery ----------

    @Test
    @DisplayName("formatPath：有根时补前导斜杠，无根时不补；逐段编码")
    void formatPathRootHandling() {
        StringBuilder rooted = new StringBuilder();
        URIHelper.formatPath(rooted, List.of("a", "b c"), false, StandardCharsets.UTF_8);
        assertEquals("/a/b%20c", rooted.toString());

        StringBuilder rootless = new StringBuilder();
        URIHelper.formatPath(rootless, List.of("a", "b c"), true, StandardCharsets.UTF_8);
        assertEquals("a/b%20c", rootless.toString());
    }

    @Test
    @DisplayName("formatQuery：null 值只输出名称；逐项编码")
    void formatQueryValues() {
        StringBuilder buf = new StringBuilder();
        URIHelper.formatQuery(buf, List.of(nvp("a", "1"), nvp("b", null), nvp("c d", "e/f")),
                StandardCharsets.UTF_8);
        assertEquals("a=1&b&c%20d=e%2Ff", buf.toString());
    }
}
