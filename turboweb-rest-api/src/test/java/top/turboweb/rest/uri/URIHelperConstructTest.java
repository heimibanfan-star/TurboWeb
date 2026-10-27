package top.turboweb.rest.uri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URIHelper 构造与 {@link URI} 消化（digest）行为：各组件的取值口径、
 * 编码原样的保留（toString / build 不重新编码）、以及 IPv6、空路径、
 * 无法直读主机时的回退解析等边界。
 */
class URIHelperConstructTest {

    private static ParamPair nvp(String name, String value) {
        return new ParamPair(name, value);
    }

    @Test
    @DisplayName("完整 URI 各组件按解码口径取值")
    void digestFullUriComponents() throws URISyntaxException {
        URIHelper helper = new URIHelper(
                "https://user:pass@www.example.com:8080/path/a%20b?x=1&y=%E4%B8%AD#frag%20x");

        assertAll(
                () -> assertEquals("https", helper.getScheme()),
                () -> assertEquals("user:pass", helper.getUserInfo()),
                () -> assertEquals("www.example.com", helper.getHost()),
                () -> assertEquals(8080, helper.getPort()),
                () -> assertEquals(List.of("path", "a b"), helper.getPathSegments()),
                () -> assertEquals(List.of(nvp("x", "1"), nvp("y", "中")), helper.getQueryParams()),
                () -> assertEquals("frag x", helper.getFragment()),
                () -> assertEquals("https://user:pass@www.example.com:8080/path/a%20b?x=1&y=%E4%B8%AD#frag%20x",
                        helper.toString()),
                () -> assertTrue(helper.isAbsolute()),
                () -> assertFalse(helper.isOpaque()),
                () -> assertFalse(helper.isPathEmpty()),
                () -> assertFalse(helper.isQueryEmpty()));
    }

    @Test
    @DisplayName("build 返回与原串等价的 URI，编码原样保留")
    void buildPreservesRawParts() throws URISyntaxException {
        URIHelper helper = new URIHelper(
                "http://ex%41mple.com/p%61th?q=%2B#%E4%B8%AD");

        URI built = helper.build();

        assertEquals("http://ex%41mple.com/p%61th?q=%2B#%E4%B8%AD", built.toString());
        // 主机与路径的百分号编码在组件层被解码
        assertEquals("exAmple.com", helper.getHost());
        assertEquals(List.of("path"), helper.getPathSegments());
    }

    @Test
    @DisplayName("省略端口时端口为 -1")
    void missingPortIsNegativeOne() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://example.com/path");

        assertEquals(-1, helper.getPort());
        assertEquals("http://example.com/path", helper.toString());
    }

    @Test
    @DisplayName("显式空端口按 -1 处理")
    void emptyPortTreatedAsDefault() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://example.com:/path");

        assertEquals(-1, helper.getPort());
    }

    @Test
    @DisplayName("IPv6 主机去除方括号取值，toString 保留方括号")
    void ipv6HostWithoutBrackets() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://[2001:db8::1]:8443/idx");

        assertAll(
                () -> assertEquals("2001:db8::1", helper.getHost()),
                () -> assertEquals(8443, helper.getPort()),
                () -> assertEquals("http://[2001:db8::1]:8443/idx", helper.toString()),
                () -> assertEquals("2001:db8::1", helper.getAuthority().getHostName()));
    }

    @Test
    @DisplayName("不透明 URI：主机为空，路径与查询视为空")
    void opaqueUriComponents() throws URISyntaxException {
        URIHelper helper = new URIHelper("mailto:a@b.com");

        assertAll(
                () -> assertEquals("mailto", helper.getScheme()),
                () -> assertNull(helper.getHost()),
                () -> assertNull(helper.getUserInfo()),
                () -> assertEquals("a@b.com", helper.getSchemeSpecificPart()),
                () -> assertTrue(helper.getPathSegments().isEmpty()),
                () -> assertTrue(helper.getQueryParams().isEmpty()),
                () -> assertTrue(helper.isPathEmpty()),
                () -> assertTrue(helper.isQueryEmpty()),
                // 路径段列表在消化阶段已初始化为空列表，故不透明 URI 的判定为 false
                () -> assertFalse(helper.isOpaque()),
                () -> assertTrue(helper.isAbsolute()));
    }

    @Test
    @DisplayName("空实例：各组件为空，build 得到空 URI")
    void emptyInstance() throws URISyntaxException {
        URIHelper helper = new URIHelper();

        assertAll(
                () -> assertNull(helper.getScheme()),
                () -> assertNull(helper.getUserInfo()),
                () -> assertNull(helper.getHost()),
                () -> assertEquals(-1, helper.getPort()),
                () -> assertTrue(helper.getPathSegments().isEmpty()),
                () -> assertTrue(helper.getQueryParams().isEmpty()),
                () -> assertNull(helper.getFragment()),
                () -> assertFalse(helper.isAbsolute()),
                () -> assertTrue(helper.isOpaque()),
                () -> assertTrue(helper.isPathEmpty()),
                () -> assertTrue(helper.isQueryEmpty()),
                () -> assertEquals("", helper.toString()),
                () -> assertEquals("", helper.build().toString()));
    }

    @Test
    @DisplayName("仅主机名无路径：路径段为空且编码路径为空串")
    void hostOnlyUri() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://example.com");

        assertAll(
                () -> assertTrue(helper.getPathSegments().isEmpty()),
                () -> assertTrue(helper.isPathEmpty()),
                () -> assertEquals("http://example.com", helper.toString()));
    }

    @Test
    @DisplayName("带查询无路径：路径仍为空")
    void queryWithoutPath() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://example.com?x=1");

        assertTrue(helper.getPathSegments().isEmpty());
        assertEquals("http://example.com?x=1", helper.toString());
    }

    @Test
    @DisplayName("主机含百分号编码时回退解析授权：解码主机与用户信息")
    void fallbackAuthorityParse() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://us%65r@ex%41mple.com/");

        assertAll(
                () -> assertEquals("user", helper.getUserInfo()),
                () -> assertEquals("exAmple.com", helper.getHost()),
                () -> assertEquals(-1, helper.getPort()),
                () -> assertEquals("http://us%65r@ex%41mple.com/", helper.toString()));
    }

    @Test
    @DisplayName("file 协议空授权：主机为 null，路径段含冒号段")
    void fileUriEmptyAuthority() throws URISyntaxException {
        URIHelper helper = new URIHelper("file:///c:/x");

        assertAll(
                () -> assertNull(helper.getHost()),
                () -> assertEquals(List.of("c:", "x"), helper.getPathSegments()),
                () -> assertEquals("file:///c:/x", helper.toString()));
    }

    @Test
    @DisplayName("指定字符集构造：按该字符集解码组件")
    void constructorWithCharset() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/%E4?k=%E4", StandardCharsets.ISO_8859_1);

        assertAll(
                () -> assertEquals(List.of("ä"), helper.getPathSegments()),
                () -> assertEquals(List.of(nvp("k", "ä")), helper.getQueryParams()));
    }

    @Test
    @DisplayName("URI 对象构造与字符串构造等价")
    void constructorFromUriObject() throws URISyntaxException {
        URI uri = URI.create("http://example.com/a?b=c");
        URIHelper fromUri = new URIHelper(uri);
        URIHelper fromString = new URIHelper("http://example.com/a?b=c");

        assertEquals(fromString.toString(), fromUri.toString());
        assertEquals(fromString.getQueryParams(), fromUri.getQueryParams());
    }

    @Test
    @DisplayName("非 URI 字符串构造抛 URISyntaxException")
    void invalidStringThrows() {
        assertThrows(URISyntaxException.class, () -> new URIHelper("http://ex ample.com"));
    }

    @Test
    @DisplayName("http 与 https 协议不允许空主机名")
    void httpSchemeRequiresHost() {
        URIHelper httpHelper = new URIHelper().setScheme("http");
        URIHelper httpsHelper = new URIHelper().setScheme("HTTPS");

        assertAll(
                () -> {
                    URISyntaxException ex = assertThrows(URISyntaxException.class, httpHelper::build);
                    assertEquals("http", ex.getInput());
                },
                () -> assertThrows(URISyntaxException.class, httpsHelper::build));
    }

    @Test
    @DisplayName("非 http 协议允许空主机名")
    void nonHttpSchemeAllowsEmptyHost() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("ftp").setPathSegmentsRootless("a");

        assertEquals("ftp:a", helper.build().toString());
    }

    @Test
    @DisplayName("loopbackAddress 以环回地址为主机")
    void loopbackAddressFactory() throws UnknownHostException {
        URIHelper helper = URIHelper.loopbackAddress();

        assertEquals(InetAddress.getLoopbackAddress().getHostAddress(), helper.getHost());
    }

    @Test
    @DisplayName("localhost 以本机地址为主机")
    void localhostFactory() {
        assertDoesNotThrow(() -> {
            URIHelper helper = URIHelper.localhost();
            assertEquals(InetAddress.getLocalHost().getHostAddress(), helper.getHost());
        });
    }

    @Test
    @DisplayName("路径段与查询参数为防御性副本")
    void gettersReturnDefensiveCopies() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a/b?x=1");

        List<String> segments = helper.getPathSegments();
        segments.clear();
        List<ParamPair> params = helper.getQueryParams();
        params.clear();

        assertAll(
                () -> assertEquals(List.of("a", "b"), helper.getPathSegments()),
                () -> assertEquals(List.of(nvp("x", "1")), helper.getQueryParams()));
    }
}
