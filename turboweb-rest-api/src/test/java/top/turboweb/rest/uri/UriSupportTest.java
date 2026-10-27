package top.turboweb.rest.uri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * uri 包支撑值对象与编解码原语的行为契约：
 * 名称值对、URI 授权、百分号编解码、主机解析与格式化。
 */
class UriSupportTest {

    // ---------- ParamPair ----------

    @Test
    @DisplayName("ParamPair：名称为 null 抛 NullPointerException")
    void paramPairNullName() {
        assertThrows(NullPointerException.class, () -> new ParamPair(null, "v"));
    }

    @Test
    @DisplayName("ParamPair：取值访问器与 toString")
    void paramPairAccessorsAndToString() {
        ParamPair nvp = new ParamPair("n", "v");

        assertAll(
                () -> assertEquals("n", nvp.name()),
                () -> assertEquals("v", nvp.value()),
                () -> assertEquals("ParamPair[name=n, value=v]", nvp.toString()),
                () -> assertEquals("ParamPair[name=n, value=null]", new ParamPair("n", null).toString()));
    }

    @Test
    @DisplayName("ParamPair：相等按名称与值严格比较")
    void paramPairEquality() {
        assertAll(
                () -> assertEquals(new ParamPair("n", "v"), new ParamPair("n", "v")),
                () -> assertEquals(new ParamPair("n", "v").hashCode(), new ParamPair("n", "v").hashCode()),
                () -> assertEquals(new ParamPair("n", null), new ParamPair("n", null)),
                () -> assertNotEquals(new ParamPair("n", "a"), new ParamPair("n", "b")),
                () -> assertNotEquals(new ParamPair("n", null), new ParamPair("n", "")),
                () -> assertNotEquals(new ParamPair("N", "v"), new ParamPair("n", "v")),
                () -> assertNotEquals(new ParamPair("n", "v"), "n=v"));
    }

    @Test
    @DisplayName("ParamPair：自反相等")
    void paramPairSelfEquality() {
        ParamPair nvp = new ParamPair("n", "v");

        assertEquals(nvp, nvp);
    }

    // ---------- URIAuthority ----------

    @Test
    @DisplayName("URIAuthority：三元构造与取值")
    void uriAuthorityGetters() {
        URIAuthority authority = new URIAuthority("u", "example.com", 8080);

        assertAll(
                () -> assertEquals("u", authority.getUserInfo()),
                () -> assertEquals("example.com", authority.getHostName()),
                () -> assertEquals(8080, authority.getPort()));
    }

    @Test
    @DisplayName("URIAuthority：双参与单参构造缺省端口为 -1")
    void uriAuthorityDefaultPort() {
        assertAll(
                () -> assertEquals(-1, new URIAuthority("example.com", -1).getPort()),
                () -> assertEquals(-1, new URIAuthority("example.com").getPort()),
                () -> assertNull(new URIAuthority("example.com").getUserInfo()));
    }

    @Test
    @DisplayName("URIAuthority：端口越界抛 IllegalArgumentException")
    void uriAuthorityPortRange() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new URIAuthority("h", 65536)),
                () -> assertThrows(IllegalArgumentException.class, () -> new URIAuthority("h", -2)),
                () -> assertThrows(IllegalArgumentException.class, () -> new URIAuthority("u", "h", 70000)),
                () -> assertEquals(65535, new URIAuthority("h", 65535).getPort()),
                () -> assertEquals(-1, new URIAuthority("h", -1).getPort()));
    }

    @Test
    @DisplayName("URIAuthority：自反相等与异型不等")
    void uriAuthoritySelfEqualityAndTypeMismatch() {
        URIAuthority authority = new URIAuthority("u", "h", 80);

        assertAll(
                () -> assertEquals(authority, authority),
                () -> assertNotEquals(authority, "u@h:80"));
    }

    @Test
    @DisplayName("URIAuthority：哈希值按用户信息与主机端口计算")
    void uriAuthorityHashCodeFormula() {
        URIAuthority authority = new URIAuthority("u", "EXAMPLE.com", 80);
        int hostHash = 17;
        hostHash = hostHash * 31 + "example.com".hashCode();
        hostHash = hostHash * 31 + 80;
        int expected = 17;
        expected = expected * 31 + "u".hashCode();
        expected = expected * 31 + hostHash;

        assertEquals(expected, authority.hashCode());
    }

    @Test
    @DisplayName("URIAuthority：NamedEndpoint 构造复制主机与端口")
    void uriAuthorityFromNamedEndpoint() {
        URIAuthority source = new URIAuthority("h", 80);

        assertAll(
                () -> assertEquals("h", new URIAuthority(source).getHostName()),
                () -> assertEquals(80, new URIAuthority(source).getPort()),
                () -> assertEquals("h", new URIAuthority("u", source).getHostName()),
                () -> assertEquals("u", new URIAuthority("u", source).getUserInfo()));
    }

    @Test
    @DisplayName("URIAuthority：toString 各形态")
    void uriAuthorityToString() {
        assertAll(
                () -> assertEquals("u@h:80", new URIAuthority("u", "h", 80).toString()),
                () -> assertEquals("u@h", new URIAuthority("u", "h", -1).toString()),
                () -> assertEquals("h", new URIAuthority("h", -1).toString()),
                () -> assertEquals("[::1]:80", new URIAuthority("::1", 80).toString()));
    }

    @Test
    @DisplayName("URIAuthority：主机比较忽略大小写")
    void uriAuthorityEqualsIgnoreCaseHost() {
        assertEquals(new URIAuthority("EXAMPLE.com", 80), new URIAuthority("example.COM", 80));
        assertEquals(new URIAuthority("EXAMPLE.com", 80).hashCode(), new URIAuthority("example.COM", 80).hashCode());
        assertNotEquals(new URIAuthority("u", "h", 80), new URIAuthority("h", 80));
        assertNotEquals(new URIAuthority("h", 80), new URIAuthority("h", 81));
    }

    @Test
    @DisplayName("URIAuthority：非 ASCII 主机 toString 转为 ASCII")
    void uriAuthorityNonAsciiHostToString() {
        assertEquals("xn--bcher-kva", new URIAuthority("bücher", -1).toString());
    }

    // ---------- PercentCodec ----------

    @Test
    @DisplayName("encode：非保留字符直通，其余按 UTF-8 百分号编码")
    void encodeUnreserved() {
        assertEquals("aZ09-._~", PercentCodec.encode("aZ09-._~", StandardCharsets.UTF_8));
        assertEquals("a%20b%2Bc%25d%2Fe", PercentCodec.encode("a b+c%d/e", StandardCharsets.UTF_8));
        assertEquals("%E4%B8%AD", PercentCodec.encode("中", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("encode：指定字符集与 null 输入")
    void encodeCharsetAndNull() {
        assertEquals("%E4", PercentCodec.encode("ä", StandardCharsets.ISO_8859_1));
        assertNull(PercentCodec.encode(null, StandardCharsets.UTF_8));
        StringBuilder buf = new StringBuilder("x");
        PercentCodec.encode(buf, null, StandardCharsets.UTF_8);
        assertEquals("x", buf.toString());
    }

    @Test
    @DisplayName("encode：URIC 字集保留子分隔符，编码广义分隔符")
    void encodeWithUric() {
        StringBuilder buf = new StringBuilder();
        PercentCodec.encode(buf, "!$&'()*+,;=:/?#[]@", StandardCharsets.UTF_8, PercentCodec.URIC);
        assertEquals("!$&'()*+,;=%3A%2F%3F%23%5B%5D%40", buf.toString());
    }

    @Test
    @DisplayName("decode：合法百分号解码，非法序列原样保留")
    void decodeValidAndMalformed() {
        assertAll(
                () -> assertEquals("A", PercentCodec.decode("%41", StandardCharsets.UTF_8)),
                () -> assertEquals("a%", PercentCodec.decode("a%", StandardCharsets.UTF_8)),
                () -> assertEquals("%G1", PercentCodec.decode("%G1", StandardCharsets.UTF_8)),
                () -> assertEquals("+", PercentCodec.decode("+", StandardCharsets.UTF_8)),
                () -> assertNull(PercentCodec.decode(null, StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("decode：非法 UTF-8 字节序列解码为替换符")
    void decodeInvalidUtf8() {
        assertEquals("�", PercentCodec.decode("%C3", StandardCharsets.UTF_8));
    }

    // ---------- Host ----------

    @Test
    @DisplayName("Host.parse：主机与端口")
    void hostParsePlain() throws URISyntaxException {
        Host host = Host.parse("example.com:8080");

        assertAll(
                () -> assertEquals("example.com", host.getHostName()),
                () -> assertEquals(8080, host.getPort()));
    }

    @Test
    @DisplayName("Host.parse：无端口")
    void hostParseNoPort() throws URISyntaxException {
        Host host = Host.parse("example.com");

        assertEquals(-1, host.getPort());
    }

    @Test
    @DisplayName("Host.parse：方括号 IPv6 与端口")
    void hostParseIpv6() throws URISyntaxException {
        Host host = Host.parse("[2001:db8::1]:80");

        assertAll(
                () -> assertEquals("2001:db8::1", host.getHostName()),
                () -> assertEquals(80, host.getPort()));
    }

    @Test
    @DisplayName("Host.parse：缺少右括号抛异常")
    void hostParseMissingClosingBracket() {
        URISyntaxException ex = assertThrows(URISyntaxException.class, () -> Host.parse("[::1:80"));
        assertEquals("Expected an IPv6 closing bracket ']'", ex.getReason());
    }

    @Test
    @DisplayName("Host.parse：括号内非 IPv6 抛异常")
    void hostParseInvalidIpv6() {
        URISyntaxException ex = assertThrows(URISyntaxException.class, () -> Host.parse("[abc]:80"));
        assertEquals("Expected an IPv6 address", ex.getReason());
    }

    @Test
    @DisplayName("Host.parse：非数字端口抛异常")
    void hostParseInvalidPort() {
        URISyntaxException ex = assertThrows(URISyntaxException.class, () -> Host.parse("h:abc"));
        assertEquals("Port is invalid", ex.getReason());
    }

    @Test
    @DisplayName("Host.parse：非括号主机带多冒号端口抛异常")
    void hostParseColonInPort() {
        URISyntaxException ex = assertThrows(URISyntaxException.class, () -> Host.parse("h:1:2"));
        assertEquals("Expected IPv6 address to be enclosed in brackets", ex.getReason());
    }

    @Test
    @DisplayName("Host：Punycode 名称转换为 Unicode")
    void hostPunycodeDecoded() {
        assertEquals("bücher", new Host("xn--bcher-kva", -1).getHostName());
        assertEquals("example.com", new Host("example.com", -1).getHostName());
    }

    @Test
    @DisplayName("Host：格式化补 IPv6 方括号与端口")
    void hostFormat() {
        StringBuilder buf = new StringBuilder();
        Host.format(buf, new Host("::1", 8080));
        assertEquals("[::1]:8080", buf.toString());

        StringBuilder noPort = new StringBuilder();
        Host.format(noPort, new Host("h", -1));
        assertEquals("h", noPort.toString());
    }

    @Test
    @DisplayName("Host：名称为 null 抛 NullPointerException")
    void hostNullName() {
        assertThrows(NullPointerException.class, () -> new Host(null, -1));
    }

    @Test
    @DisplayName("Host：相等性与哈希按小写名称与端口")
    void hostEqualsAndHashCode() {
        Host a = new Host("EXAMPLE.com", 80);
        Host b = new Host("example.com", 80);
        Host c = new Host("example.com", 81);

        assertAll(
                () -> assertEquals(a, b),
                () -> assertEquals(a.hashCode(), b.hashCode()),
                () -> assertNotEquals(a, c),
                () -> assertNotEquals(a, "example.com"),
                () -> assertEquals(a, a));
    }

    @Test
    @DisplayName("Host：哈希值按小写名称与端口计算")
    void hostHashCodeFormula() {
        Host host = new Host("EXAMPLE.com", 80);
        int expected = 17;
        expected = expected * 31 + "example.com".hashCode();
        expected = expected * 31 + 80;

        assertEquals(expected, host.hashCode());
    }

    @Test
    @DisplayName("Host：toString 输出主机形态")
    void hostToString() {
        assertAll(
                () -> assertEquals("[::1]:80", new Host("::1", 80).toString()),
                () -> assertEquals("h", new Host("h", -1).toString()));
    }

    @Test
    @DisplayName("Host.parse：异常输入以游标区间为输入串")
    void hostParseExceptionUsesCursorBounds() {
        URISyntaxException ex = assertThrows(URISyntaxException.class,
                () -> Host.parse("xxh:abcyy", new UriTokenizer.Cursor(2, 9)));

        assertEquals("h:abcyy", ex.getInput());
    }

    @Test
    @DisplayName("Host：大写 Punycode 前缀同样转换")
    void hostPunycodeCaseInsensitivePrefix() {
        assertEquals("bücher", new Host("XN--bcher-kva", -1).getHostName());
    }

    // ---------- IPv6 判定（经 URIHelper 行为验证） ----------

    @Test
    @DisplayName("带作用域 ID 的 IPv6 地址构建时补方括号")
    void scopedIpv6Host() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("fe80::1%25eth0");

        assertEquals("http://[fe80::1%25eth0]", helper.build().toString());
    }

    @Test
    @DisplayName("空作用域 ID 不视为 IPv6，构建时整体编码")
    void emptyScopeIdNotIpv6() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("fe80::1%");

        assertEquals("http://fe80%3A%3A1%25", helper.build().toString());
    }

    @Test
    @DisplayName("满 7 个冒号的标准形 IPv6 构建时补方括号")
    void fullFormIpv6Host() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("1:2:3:4:5:6:7:8");

        assertEquals("http://[1:2:3:4:5:6:7:8]", helper.build().toString());
    }

    @Test
    @DisplayName("带作用域 ID 的标准形 IPv6 构建时补方括号")
    void scopedStdFormIpv6Host() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("1:2:3:4:5:6:7:8%eth0");

        assertEquals("http://[1:2:3:4:5:6:7:8%eth0]", helper.build().toString());
    }

    @Test
    @DisplayName("作用域部分非法的地址不视为 IPv6，构建时整体编码")
    void invalidScopedAddressNotIpv6() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("abc%eth0");

        assertEquals("http://abc%25eth0", helper.build().toString());
    }

    @Test
    @DisplayName("压缩形两侧字段合计超限时不视为 IPv6，构建时整体编码")
    void overlongCompressedIpv6Rejected() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("1:2:3:4:5:6::7:8");

        assertEquals("http://1%3A2%3A3%3A4%3A5%3A6%3A%3A7%3A8", helper.build().toString());
    }

    // ---------- URIHelper 方括号 IPv6 判定 ----------

    @Test
    @DisplayName("isIPv6URLBracketed：方括号形态判定")
    void isIPv6URLBracketedForms() {
        assertAll(
                () -> assertTrue(URIHelper.isIPv6URLBracketed("[::1]")),
                () -> assertFalse(URIHelper.isIPv6URLBracketed("")),
                () -> assertFalse(URIHelper.isIPv6URLBracketed("[abc]")),
                () -> assertFalse(URIHelper.isIPv6URLBracketed("[::1")),
                () -> assertFalse(URIHelper.isIPv6URLBracketed("::1")));
    }

    // ---------- UriTextUtils ----------

    @Test
    @DisplayName("toLowerCase：null 输入返回 null")
    void toLowerCaseNull() {
        assertNull(UriTextUtils.toLowerCase(null));
    }
}
