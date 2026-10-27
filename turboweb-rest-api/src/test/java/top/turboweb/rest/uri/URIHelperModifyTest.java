package top.turboweb.rest.uri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URIHelper 各设置方法的行为契约：取值归一（空白串归 null）、
 * 编码原样的失效时机、相对与绝对路径的拼接口径、查询参数的增删改查。
 */
class URIHelperModifyTest {

    private static ParamPair nvp(String name, String value) {
        return new ParamPair(name, value);
    }

    // ---------- scheme ----------

    @Test
    @DisplayName("setScheme：空白值归 null")
    void setSchemeBlankBecomesNull() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setScheme("   ");

        assertNull(helper.getScheme());
        assertTrue(helper.isAbsolute() == false);
    }

    @Test
    @DisplayName("setScheme 保留编码原样的失效：schemeSpecificPart 重算")
    void setSchemeKeepsSchemeSpecificPartUntilComponentChanged() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1");
        helper.setScheme("https");
        assertEquals("https://h/p?a=1", helper.build().toString());

        helper.setHost("h2");
        assertEquals("https://h2/p?a=1", helper.build().toString());
    }

    // ---------- host ----------

    @Test
    @DisplayName("setHost(String)：IPv6 地址构建时补方括号")
    void setHostIpv6BracketedOnBuild() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("::1").setPort(8080);

        assertEquals("http://[::1]:8080", helper.build().toString());
    }

    @Test
    @DisplayName("setHost(String)：非 IPv6 主机构建时按非保留字集编码")
    void setHostEncodedOnBuild() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("ex ample.com");

        assertEquals("http://ex%20ample.com", helper.build().toString());
    }

    @Test
    @DisplayName("setHost(InetAddress)：取点分地址；null 归 null")
    void setHostInetAddress() throws Exception {
        URIHelper helper = new URIHelper();
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        helper.setHost(loopback);
        assertEquals(loopback.getHostAddress(), helper.getHost());

        helper.setHost((InetAddress) null);
        assertNull(helper.getHost());
    }

    @Test
    @DisplayName("setHost 使 schemeSpecificPart 与编码授权失效")
    void setHostInvalidatesEncodedParts() throws URISyntaxException {
        URIHelper helper = new URIHelper("mailto:a@b.com");
        helper.setHost("example.com");

        assertNull(helper.getSchemeSpecificPart());
        assertEquals("example.com", helper.getHost());
    }

    // ---------- port ----------

    @Test
    @DisplayName("setPort：负值归 -1")
    void setPortNegativeBecomesDefault() throws URISyntaxException {
        URIHelper helper = new URIHelper().setPort(-100);

        assertEquals(-1, helper.getPort());
    }

    @Test
    @DisplayName("setPort：0 为合法端口，构建输出 :0")
    void setPortZeroKept() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("h").setPort(0);

        assertAll(
                () -> assertEquals(0, helper.getPort()),
                () -> assertEquals("http://h:0", helper.build().toString()));
    }

    @Test
    @DisplayName("编码路径为空串且有授权：构建不补前导斜杠")
    void encodedPathEmptyWithAuthorityNoSlash() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h").setPort(80);

        assertEquals("http://h:80", helper.build().toString());
    }

    @Test
    @DisplayName("编码路径为无根形态且有授权：构建补前导斜杠")
    void encodedPathRootlessWithAuthorityPrependsSlash() throws URISyntaxException {
        URIHelper helper = new URIHelper("rootless/path").setScheme("x").setHost("h");

        assertEquals("x://h/rootless/path", helper.build().toString());
    }

    @Test
    @DisplayName("setPort 使编码授权失效")
    void setPortInvalidatesEncodedAuthority() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://ex%41mple.com/p");
        helper.setPort(80);

        assertEquals("http://exAmple.com:80/p", helper.build().toString());
    }

    // ---------- path ----------

    @Test
    @DisplayName("setPath：按斜杠切段并解码；无斜杠开头视为无根路径")
    void setPathSplitsAndDecodes() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").setPath("b/c d");

        assertAll(
                () -> assertEquals(List.of("b", "c d"), helper.getPathSegments()),
                () -> assertEquals("http://h/b/c%20d", helper.build().toString()));
    }

    @Test
    @DisplayName("setPath：null 清空路径")
    void setPathNullClears() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").setPath(null);

        assertTrue(helper.getPathSegments().isEmpty());
        assertTrue(helper.isPathEmpty());
    }

    @Test
    @DisplayName("无根路径：无授权时构建不加前导斜杠")
    void rootlessPathWithoutAuthority() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x").setPath("api/v1");

        assertEquals("x:api/v1", helper.build().toString());
    }

    @Test
    @DisplayName("无根路径：有授权时构建补前导斜杠")
    void rootlessPathWithAuthority() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("h").setPath("api/v1");

        assertEquals("http://h/api/v1", helper.build().toString());
    }

    @Test
    @DisplayName("appendPath：在既有路径段之后追加")
    void appendPathAppends() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").appendPath("b/c");

        assertEquals(List.of("a", "b", "c"), helper.getPathSegments());
    }

    @Test
    @DisplayName("appendPath：null 不变")
    void appendPathNullNoOp() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").appendPath(null);

        assertEquals(List.of("a"), helper.getPathSegments());
    }

    @Test
    @DisplayName("setPathSegments(String...)：设置并编码各段")
    void setPathSegmentsVarargs() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h").setPathSegments("a b", "c%d");

        assertEquals("http://h/a%20b/c%25d", helper.build().toString());
    }

    @Test
    @DisplayName("setPathSegments(List)：空列表或 null 清空路径")
    void setPathSegmentsListEmptyOrNull() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a");

        helper.setPathSegments((List<String>) null);
        assertTrue(helper.getPathSegments().isEmpty());

        helper.setPathSegments(Collections.emptyList());
        assertTrue(helper.getPathSegments().isEmpty());
    }

    @Test
    @DisplayName("appendPathSegments(String...)：追加并编码")
    void appendPathSegmentsVarargs() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").appendPathSegments("b c", "d");

        assertEquals("http://h/a/b%20c/d", helper.build().toString());
    }

    @Test
    @DisplayName("appendPathSegments(List)：无路径时新建；空列表或 null 不变")
    void appendPathSegmentsListEdgeCases() throws URISyntaxException {
        URIHelper helper = new URIHelper().appendPathSegments((List<String>) null);
        assertTrue(helper.getPathSegments().isEmpty());

        helper.appendPathSegments(List.of("a"));
        assertEquals(List.of("a"), helper.getPathSegments());

        helper.appendPathSegments(Collections.emptyList());
        assertEquals(List.of("a"), helper.getPathSegments());
    }

    @Test
    @DisplayName("setPathSegmentsRootless：无授权构建时不加前导斜杠")
    void setPathSegmentsRootless() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x").setPathSegmentsRootless("api", "v1");

        assertEquals("x:api/v1", helper.build().toString());
    }

    @Test
    @DisplayName("setPathSegmentsRootless：有授权时仍补前导斜杠")
    void setPathSegmentsRootlessWithAuthority() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("http").setHost("h").setPathSegmentsRootless("api");

        assertEquals("http://h/api", helper.build().toString());
    }

    @Test
    @DisplayName("setPathSegmentsRootless(String...)：null 数组抛 NullPointerException")
    void setPathSegmentsRootlessNullArray() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a");

        assertThrows(NullPointerException.class, () -> helper.setPathSegmentsRootless((String[]) null));
    }

    @Test
    @DisplayName("getPath：各段前置斜杠拼接（不做百分号编码）")
    void getPathJoinsSegments() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a%20b");

        assertEquals("/a b", helper.getPath());
    }

    @Test
    @DisplayName("getPath：路径段列表初始化为空时返回空串")
    void getPathNullWhenNoSegments() throws URISyntaxException {
        URIHelper helper = new URIHelper("mailto:a@b");

        assertEquals("", helper.getPath());
    }

    @Test
    @DisplayName("getPath：路径段列表被置空后为 null")
    void getPathNullAfterClearingSegments() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/a").setPath(null);

        assertNull(helper.getPath());
    }

    // ---------- query ----------

    @Test
    @DisplayName("setParameters(List)：整体替换")
    void setParametersListReplaces() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?old=1")
                .setParameters(List.of(nvp("a", "1"), nvp("b", null)));

        assertAll(
                () -> assertEquals(List.of(nvp("a", "1"), nvp("b", null)), helper.getQueryParams()),
                () -> assertEquals("http://h/p?a=1&b", helper.build().toString()));
    }

    @Test
    @DisplayName("setParameters(List)：null 清空参数")
    void setParametersListNullClears() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?old=1").setParameters((List<ParamPair>) null);

        assertTrue(helper.getQueryParams().isEmpty());
        assertTrue(helper.isQueryEmpty());
    }

    @Test
    @DisplayName("setParameters(ParamPair...)：整体替换")
    void setParametersVarargs() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?old=1")
                .setParameters(nvp("a", "1"), nvp("b", "2"));

        assertEquals(Arrays.asList(nvp("a", "1"), nvp("b", "2")), helper.getQueryParams());
    }

    @Test
    @DisplayName("setParameters(ParamPair...)：null 清空参数")
    void setParametersVarargsNull() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?old=1").setParameters((ParamPair[]) null);

        assertTrue(helper.getQueryParams().isEmpty());
    }

    @Test
    @DisplayName("setParameters(List)：参数列表未初始化时正常初始化")
    void setParametersListInitializesWhenNull() throws URISyntaxException {
        URIHelper helper = new URIHelper().setCustomQuery("a=1").setParameters((List<ParamPair>) null);

        assertTrue(helper.getQueryParams().isEmpty());
        assertTrue(helper.isQueryEmpty());
    }

    @Test
    @DisplayName("setParameters(ParamPair...)：参数列表未初始化时正常初始化")
    void setParametersVarargsInitializesWhenNull() throws URISyntaxException {
        URIHelper helper = new URIHelper().setCustomQuery("a=1").setParameters((ParamPair[]) null);

        assertTrue(helper.getQueryParams().isEmpty());
        assertTrue(helper.isQueryEmpty());
    }

    @Test
    @DisplayName("addParameters(List)：在既有参数后追加")
    void addParametersAppends() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1")
                .addParameters(List.of(nvp("b", "2")));

        assertEquals("http://h/p?a=1&b=2", helper.build().toString());
    }

    @Test
    @DisplayName("addParameters(List)：null 不变")
    void addParametersNullNoOp() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1").addParameters(null);

        assertEquals(List.of(nvp("a", "1")), helper.getQueryParams());
    }

    @Test
    @DisplayName("addParameters：自定义查询被清除")
    void addParametersClearsCustomQuery() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setCustomQuery("a=1")
                .addParameters(List.of(nvp("b", "2")));

        assertEquals("http://h/p?b=2", helper.build().toString());
    }

    @Test
    @DisplayName("addParameter(String, String)：null 值只输出参数名")
    void addParameterNullValue() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").addParameter("k", null);

        assertEquals("http://h/p?k", helper.build().toString());
    }

    @Test
    @DisplayName("addParameter(String, String)：空值输出等号")
    void addParameterEmptyValue() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").addParameter("k", "");

        assertEquals("http://h/p?k=", helper.build().toString());
    }

    @Test
    @DisplayName("addParameter(ParamPair)：null 参数不变")
    void addParameterNvpNullNoOp() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1").addParameter((ParamPair) null);

        assertEquals(List.of(nvp("a", "1")), helper.getQueryParams());
    }

    @Test
    @DisplayName("removeParameter：删除同名全部参数")
    void removeParameterRemovesAllMatching() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1&b=2&a=3").removeParameter("a");

        assertEquals(List.of(nvp("b", "2")), helper.getQueryParams());
        assertEquals("http://h/p?b=2", helper.build().toString());
    }

    @Test
    @DisplayName("removeParameter：未命中也清除自定义查询")
    void removeParameterClearsCustomQueryEvenIfNoMatch() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setCustomQuery("a=1").removeParameter("zzz");

        assertTrue(helper.isQueryEmpty());
        assertEquals("http://h/p", helper.build().toString());
    }

    @Test
    @DisplayName("removeParameter：参数名为 null 抛 NullPointerException")
    void removeParameterNullName() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1");

        assertThrows(NullPointerException.class, () -> helper.removeParameter(null));
    }

    @Test
    @DisplayName("setParameter：覆盖同名旧值，新值追加在尾部")
    void setParameterOverrides() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1&b=2").setParameter("a", "9");

        assertEquals(List.of(nvp("b", "2"), nvp("a", "9")), helper.getQueryParams());
        assertEquals("http://h/p?b=2&a=9", helper.build().toString());
    }

    @Test
    @DisplayName("setParameter：名称与值构建时按非保留字集编码")
    void setParameterEncodes() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setParameter("k 1", "v&l=值");

        assertEquals("http://h/p?k%201=v%26l%3D%E5%80%BC", helper.build().toString());
    }

    @Test
    @DisplayName("clearParameters：清空参数列表")
    void clearParameters() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1").clearParameters();

        assertTrue(helper.isQueryEmpty());
        assertEquals("http://h/p", helper.build().toString());
    }

    @Test
    @DisplayName("removeQuery：清除参数、自定义查询与编码原样")
    void removeQuery() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1").removeQuery();

        assertTrue(helper.isQueryEmpty());
        assertEquals("http://h/p", helper.build().toString());
    }

    @Test
    @DisplayName("setCustomQuery：空白值归 null，构建时不输出问号")
    void setCustomQueryBlankBecomesNull() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setCustomQuery("   ");

        assertTrue(helper.getQueryParams().isEmpty());
        assertEquals("http://h/p", helper.build().toString());
    }

    @Test
    @DisplayName("setCustomQuery：按 URIC 字集编码，子分隔符保留")
    void setCustomQueryEncodesWithUric() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setCustomQuery("a=1 2&b=%中");

        assertEquals("http://h/p?a=1%202&b=%25%E4%B8%AD", helper.build().toString());
    }

    @Test
    @DisplayName("setCustomQuery：清除既有参数")
    void setCustomQueryClearsParameters() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1").setCustomQuery("b=2");

        assertEquals("http://h/p?b=2", helper.build().toString());
    }

    @Test
    @DisplayName("getFirstQueryParam：返回首个同名参数；未命中为 null")
    void getFirstQueryParam() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p?a=1&b=2&a=3");

        assertAll(
                () -> assertEquals(nvp("a", "1"), helper.getFirstQueryParam("a")),
                () -> assertEquals(nvp("b", "2"), helper.getFirstQueryParam("b")),
                () -> assertNull(helper.getFirstQueryParam("zzz")));
    }

    @Test
    @DisplayName("getFirstQueryParam：参数列表未初始化时为 null")
    void getFirstQueryParamWhenNeverSet() {
        URIHelper helper = new URIHelper();

        assertNull(helper.getFirstQueryParam("a"));
    }

    // ---------- fragment ----------

    @Test
    @DisplayName("setFragment：空白值归 null；构建时按非保留字集编码")
    void setFragment() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p").setFragment("f r#x");

        assertEquals("http://h/p#f%20r%23x", helper.build().toString());
    }

    @Test
    @DisplayName("setFragment：null 使编码原样失效")
    void setFragmentNullInvalidatesEncoded() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h/p#%E4%B8%AD").setFragment(null);

        assertEquals("http://h/p", helper.build().toString());
    }

    // ---------- charset ----------

    @Test
    @DisplayName("setCharset：影响后续组件编码字符集")
    void setCharsetAffectsEncoding() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h")
                .setCharset(StandardCharsets.ISO_8859_1)
                .setPath("ä");

        assertEquals("http://h/%E4", helper.build().toString());
    }

    @Test
    @DisplayName("getCharset：返回已设置字符集；缺省为 null")
    void getCharsetDefaultNull() {
        assertAll(
                () -> assertNull(new URIHelper().getCharset()),
                () -> assertEquals(StandardCharsets.UTF_8,
                        new URIHelper().setCharset(StandardCharsets.UTF_8).getCharset()));
    }

    // ---------- schemeSpecificPart ----------

    @Test
    @DisplayName("setSchemeSpecificPart：原样拼接，构建不重新编码")
    void setSchemeSpecificPartRaw() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x").setSchemeSpecificPart("a%20b?c=d");

        assertEquals("x:a%20b?c=d", helper.build().toString());
        assertEquals("a%20b?c=d", helper.getSchemeSpecificPart());
    }

    @Test
    @DisplayName("setSchemeSpecificPart(String)：原样保存含空白值；null 回落普通构建")
    void setSchemeSpecificPartVerbatim() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x").setSchemeSpecificPart("a%20b");
        assertEquals("x:a%20b", helper.build().toString());

        URIHelper blank = new URIHelper().setScheme("x").setSchemeSpecificPart("a b");
        assertEquals("x:a b", blank.toString());

        URIHelper fallback = new URIHelper("http://h/p").setSchemeSpecificPart(null);
        assertEquals("http://h/p", fallback.build().toString());
    }

    @Test
    @DisplayName("setSchemeSpecificPart(String, List)：查询参数追加到固定部分")
    void setSchemeSpecificPartWithParameters() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x")
                .setSchemeSpecificPart("ssp", List.of(nvp("a", "1"), nvp("b", null)));

        assertEquals("x:ssp?a=1&b", helper.build().toString());
    }

    @Test
    @DisplayName("setSchemeSpecificPart(String, List)：空列表不追加问号")
    void setSchemeSpecificPartWithEmptyParameters() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x")
                .setSchemeSpecificPart("ssp", Collections.emptyList());

        assertEquals("x:ssp", helper.build().toString());
    }

    @Test
    @DisplayName("setSchemeSpecificPart(String, List)：空白固定部分归 null，构建抛异常")
    void setSchemeSpecificPartWithParametersBlankSsp() {
        URIHelper helper = new URIHelper().setScheme("x")
                .setSchemeSpecificPart("  ", List.of(nvp("a", "1")));

        assertThrows(URISyntaxException.class, helper::build);
    }

    @Test
    @DisplayName("setSchemeSpecificPart(ParamPair...)：变参重载等价列表重载")
    void setSchemeSpecificPartVarargs() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x")
                .setSchemeSpecificPart("ssp", nvp("a", "1"));

        assertEquals("x:ssp?a=1", helper.build().toString());
    }

    @Test
    @DisplayName("setSchemeSpecificPart(ParamPair...)：null 数组等价无参数")
    void setSchemeSpecificPartVarargsNull() throws URISyntaxException {
        URIHelper helper = new URIHelper().setScheme("x")
                .setSchemeSpecificPart("ssp", (ParamPair[]) null);

        assertEquals("x:ssp", helper.build().toString());
    }

    // ---------- authority ----------

    @Test
    @DisplayName("setAuthority：替换主机与端口")
    void setAuthorityFromHost() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://old.example.com:1/p")
                .setAuthority(new Host("example.com", 80));

        assertAll(
                () -> assertEquals("example.com", helper.getHost()),
                () -> assertEquals(80, helper.getPort()));
    }

    // ---------- 状态查询 ----------

    @Test
    @DisplayName("自定义查询不计入 isQueryEmpty 判定")
    void customQueryNotCountedByIsQueryEmpty() {
        URIHelper helper = new URIHelper().setCustomQuery("a=1");

        assertTrue(helper.isQueryEmpty());
        assertTrue(helper.isPathEmpty());
    }

    @Test
    @DisplayName("isPathEmpty 对空白编码路径的判定")
    void isPathEmptyForEmptyEncodedPath() throws URISyntaxException {
        URIHelper helper = new URIHelper("http://h");

        assertTrue(helper.isPathEmpty());
    }

    // ---------- 链式调用 ----------

    @Test
    @DisplayName("各设置方法返回同一实例支持链式调用")
    void settersReturnSameInstance() {
        URIHelper helper = new URIHelper();

        assertSame(helper, helper.setScheme("http"));
        assertSame(helper, helper.setHost("h"));
        assertSame(helper, helper.setPort(80));
        assertSame(helper, helper.setPath("/p"));
        assertSame(helper, helper.setFragment("f"));
        assertSame(helper, helper.setCharset(StandardCharsets.UTF_8));
        assertSame(helper, helper.setCustomQuery("a=1"));
        assertSame(helper, helper.clearParameters());
        assertSame(helper, helper.removeQuery());
        assertSame(helper, helper.optimize());
    }
}
