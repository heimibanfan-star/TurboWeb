package top.turboweb.http.core.uri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link URIBuilder#optimize()} 的行为契约：协议与主机转小写、
 * 路径点段消除（含结尾斜杠保留）、组件编码重算；无根路径时整体跳过。
 */
class URIBuilderOptimizeTest {

    @Test
    @DisplayName("scheme 与 host 转小写")
    void lowercasesSchemeAndHost() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("HTTP://WWW.Example.COM/p");

        helper.optimize();

        assertEquals("http://www.example.com/p", helper.build().toString());
    }

    @Test
    @DisplayName("点段消除：单点移除，双点回退上一段")
    void removesDotSegments() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h/a/./b/../c");

        helper.optimize();

        assertEquals("http://h/a/c", helper.build().toString());
    }

    @Test
    @DisplayName("双点超出根时停在根上")
    void doubleDotAtRootStays() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h/a/../../b");

        helper.optimize();

        assertEquals("http://h/b", helper.build().toString());
    }

    @Test
    @DisplayName("结尾斜杠以空段保留")
    void trailingSlashPreserved() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h/a/");

        helper.optimize();

        assertEquals("http://h/a/", helper.build().toString());
    }

    @Test
    @DisplayName("空路径为无根形态，优化直接跳过")
    void emptyPathBecomesRootSegment() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h");

        helper.optimize();

        assertEquals("http://h", helper.build().toString());
    }

    @Test
    @DisplayName("空段移除，仅保留结尾空段")
    void emptySegmentsRemoved() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h//a///b/");

        helper.optimize();

        assertEquals("http://h/a/b/", helper.build().toString());
    }

    @Test
    @DisplayName("无根路径时路径保持原样，仅协议转小写")
    void rootlessPathSkipped() throws URISyntaxException {
        URIBuilder helper = new URIBuilder().setScheme("X-Scheme").setPathSegmentsRootless("A", ".");

        helper.optimize();

        assertEquals("x-scheme", helper.getScheme());
        assertEquals(java.util.List.of("A", "."), helper.getPathSegments());
        assertEquals("x-scheme:A/.", helper.build().toString());
    }

    @Test
    @DisplayName("优化后组件编码重算：查询参数按非保留字集重新编码")
    void reencodesQueryAfterOptimize() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h/p?x=%41");

        helper.optimize();

        assertEquals("http://h/p?x=A", helper.build().toString());
    }

    @Test
    @DisplayName("优化后组件编码重算：路径段重新编码")
    void reencodesPathAfterOptimize() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://h/a%2Fb");

        helper.optimize();

        assertEquals("http://h/a%2Fb", helper.build().toString());
    }

    @Test
    @DisplayName("优化后组件编码重算：片段重新编码，输入残留的用户信息被丢弃")
    void reencodesFragmentAndDropsUserInfoAfterOptimize() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("http://u%20ser@h/p#f%20g");

        helper.optimize();

        assertEquals("http://h/p#f%20g", helper.build().toString());
    }

    @Test
    @DisplayName("normalizeSyntax 与 optimize 等价")
    void normalizeSyntaxAliasesOptimize() throws URISyntaxException {
        URIBuilder helper = new URIBuilder("HTTP://H/a/./b");

        java.util.List<String> segments = helper.normalizeSyntax().getPathSegments();

        assertEquals(java.util.List.of("a", "b"), segments);
        assertEquals("http://h/a/b", helper.build().toString());
    }

    @Test
    @DisplayName("无根路径时 optimize 返回本实例")
    void optimizeReturnsSelfWhenRootless() throws URISyntaxException {
        URIBuilder helper = new URIBuilder().setPathSegmentsRootless("a");

        assertEquals(java.util.List.of("a"), helper.optimize().getPathSegments());
    }
}
