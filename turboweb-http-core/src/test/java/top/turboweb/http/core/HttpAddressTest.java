package top.turboweb.http.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpAddressTest {

    @Test
    @DisplayName("三段构造以 http 开头拼出完整地址")
    void threePartComposesPath() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", "/api/v1").string();
        assertEquals("http://example.com/api/v1", address);
    }

    @Test
    @DisplayName("两段构造不含 baseUrl 路径")
    void twoPartOmitsPath() {
        String address = HttpAddress.of(HttpSchemes.Https, "example.com").string();
        assertEquals("https://example.com", address);
    }

    @Test
    @DisplayName("baseUrl 为 null 时不拼路径")
    void nullBaseUrlOmitsPath() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", null).string();
        assertEquals("http://example.com", address);
    }

    @Test
    @DisplayName("baseUrl 结尾的斜杠被剥除")
    void trailingSlashStripped() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", "api/v1/").string();
        assertEquals("http://example.com/api/v1", address);
    }

    @Test
    @DisplayName("baseUrl 开头的斜杠被剥除")
    void leadingSlashStripped() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", "/api/v1").string();
        assertEquals("http://example.com/api/v1", address);
    }

    @Test
    @DisplayName("baseUrl 内出现连续斜杠时拒绝")
    void consecutiveSlashesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com", "/api//v1"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com", "api//v1//"));
    }

    @Test
    @DisplayName("host 携带协议内容时拒绝")
    void hostWithSchemeContentRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "http://example.com", "/api"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Https, "https://example.com", null));
    }

    @Test
    @DisplayName("host 携带路径斜杠时拒绝")
    void hostWithSlashRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com/api", null));
    }

    @Test
    @DisplayName("host 为空时拒绝")
    void emptyHostRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "", null));
    }

    @Test
    @DisplayName("addressOf 解析完整地址字符串")
    void addressOfParsesFullAddress() {
        assertEquals("http://example.com/api/v1", HttpAddress.addressOf("http://example.com/api/v1").string());
        assertEquals("https://example.com", HttpAddress.addressOf("https://example.com").string());
        assertEquals("https://example.com", HttpAddress.addressOf("https://example.com/").string());
        assertEquals("http://example.com/api", HttpAddress.addressOf("  http://example.com/api  ").string());
    }

    @Test
    @DisplayName("addressOf 地址内连续斜杠拒绝")
    void addressOfConsecutiveSlashesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.addressOf("http://example.com/api//v1"));
    }

    @Test
    @DisplayName("addressOf 缺少协议头或协议不受支持时拒绝")
    void addressOfInvalidAddressRejected() {
        assertThrows(IllegalArgumentException.class, () -> HttpAddress.addressOf("example.com/api"));
        assertThrows(IllegalArgumentException.class, () -> HttpAddress.addressOf("ftp://example.com"));
        assertThrows(IllegalArgumentException.class, () -> HttpAddress.addressOf("http://"));
        assertThrows(NullPointerException.class, () -> HttpAddress.addressOf(null));
    }

    @Test
    @DisplayName("baseUrl 携带查询语义字符时拒绝")
    void baseUrlWithQuerySemanticsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com", "/api?x=1"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com", "a&b"));
    }

    @Test
    @DisplayName("host 携带查询语义字符时拒绝")
    void hostWithQuerySemanticsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com?x=1", null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com&a=1", null));
    }

    @Test
    @DisplayName("addressOf 地址携带查询串时拒绝")
    void addressOfWithQueryRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.addressOf("http://example.com/api?x=1"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.addressOf("http://example.com?x=1"));
    }

    @Test
    @DisplayName("baseUrl 与 host 携带片段语义字符时拒绝")
    void fragmentSemanticsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com", "/api#frag"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.of(HttpSchemes.Http, "example.com#frag", null));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.addressOf("http://example.com/api#frag"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpAddress.addressOf("http://example.com#frag"));
    }

    @Test
    @DisplayName("baseUrl 仅根路径时退化为纯主机地址")
    void rootOnlyBaseUrlDegradesToHost() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", "/").string();
        assertEquals("http://example.com", address);
    }

    @Test
    @DisplayName("https 带多段路径")
    void httpsWithMultiSegmentPath() {
        String address = HttpAddress.of(HttpSchemes.Https, "api.example.com", "v2/users").string();
        assertEquals("https://api.example.com/v2/users", address);
    }

    @Test
    @DisplayName("任何地址结尾都不含斜杠")
    void addressNeverEndsWithSlash() {
        String address = HttpAddress.of(HttpSchemes.Http, "example.com", "a/b/").string();
        assertFalse(address.endsWith("/"));
    }

    @Test
    @DisplayName("string 重复调用返回缓存的同一实例")
    void stringCachedAcrossCalls() {
        HttpAddress address = HttpAddress.of(HttpSchemes.Http, "example.com", "/api");
        assertSame(address.string(), address.string());
    }

    @Test
    @DisplayName("scheme 为 null 时拒绝")
    void nullSchemesRejected() {
        assertThrows(NullPointerException.class,
                () -> HttpAddress.of(null, "example.com", "/api"));
    }

    @Test
    @DisplayName("host 为 null 时拒绝")
    void nullHostRejected() {
        assertThrows(NullPointerException.class,
                () -> HttpAddress.of(HttpSchemes.Http, null, "/api"));
    }
}