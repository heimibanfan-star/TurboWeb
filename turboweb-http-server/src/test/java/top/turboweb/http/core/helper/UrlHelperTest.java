package top.turboweb.http.core.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UrlHelper} 查询解析：分段规则、百分号解码口径（加号保持字面）、
 * 片段裁剪与畸形转义的失败传播，以及默认 UTF-8 与显式字符集两条字节组装通道。
 */
class UrlHelperTest {

    private static UrlHelper.ParamPair pair(String uri, String name) {
        return pair(uri, name, StandardCharsets.UTF_8);
    }

    private static UrlHelper.ParamPair pair(String uri, String name, Charset charset) {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams(uri, charset);
        UrlHelper.ParamPair found = null;
        for (UrlHelper.ParamPair p : params) {
            if (p.getName().equals(name)) {
                found = p;
                break;
            }
        }
        return found;
    }

    @Test
    void nullUriRejected() {
        assertThrows(NullPointerException.class, () -> UrlHelper.parseQueryParams(null));
    }

    @Test
    void absentAndEmptyQueryYieldNoParams() {
        assertTrue(UrlHelper.parseQueryParams("").isEmpty());
        assertTrue(UrlHelper.parseQueryParams("/path").isEmpty());
        assertTrue(UrlHelper.parseQueryParams("/path?").isEmpty());
        // 查询串只剩片段时同样为空
        assertTrue(UrlHelper.parseQueryParams("/path?#frag").isEmpty());
    }

    @Test
    void simplePairsParsedInOrder() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("/p?a=1&b=2");
        assertEquals(2, params.size());
        assertEquals("a", params.get(0).getName());
        assertEquals("1", params.get(0).getValue());
        assertEquals("b", params.get(1).getName());
        assertEquals("2", params.get(1).getValue());
    }

    @Test
    void duplicateKeysKeepAllValues() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("/search?tag=a&tag=b");
        assertEquals(2, params.size());
        assertEquals("a", params.get(0).getValue());
        assertEquals("b", params.get(1).getValue());
    }

    @Test
    void emptySegmentsAreSkipped() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("/search?&&x=1&");
        assertEquals(1, params.size());
        assertEquals("x", params.get(0).getName());
        assertEquals("1", params.get(0).getValue());
    }

    @Test
    void segmentWithoutEqualsHasNullValue() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("/s?a&b=1");
        assertEquals(2, params.size());
        assertEquals("a", params.get(0).getName());
        assertNull(params.get(0).getValue());
    }

    @Test
    void emptyValueAndEmptyNameArePreserved() {
        UrlHelper.ParamPair emptyValue = pair("/s?a=", "a");
        assertEquals("", emptyValue.getValue());
        UrlHelper.ParamPair emptyName = pair("/s?=v", "");
        assertEquals("v", emptyName.getValue());
    }

    @Test
    void valueKeepsEverythingAfterFirstEquals() {
        assertEquals("b=c", pair("/s?a=b=c", "a").getValue());
    }

    @Test
    @DisplayName("加号是字面字符：不解码为空格（纯百分号解码口径）")
    void plusStaysLiteral() {
        assertEquals("a+b", pair("/s?q=a+b", "q").getValue());
        assertEquals("a+b", pair("/s?a+b=c", "a+b").getName());
    }

    @Test
    void percentEscapesAreDecoded() {
        assertEquals("a b", pair("/s?q=a%20b", "q").getValue());
        // %2B 解码为字面加号
        assertEquals("a+b", pair("/s?q=a%2Bb", "q").getValue());
        // 十六进制大小写均可
        assertEquals("中", pair("/s?q=%e4%b8%ad", "q").getValue());
        assertEquals("中", pair("/s?q=%E4%B8%AD", "q").getValue());
        // 名称同样解码：q%61 还原为 qa
        assertEquals("qa", pair("/s?q%61=b", "qa").getName());
    }

    @Test
    void utf8MultiByteSpansAcrossAsciiAndRawChars() {
        assertEquals("a中b", pair("/s?q=a%E4%B8%ADb", "q").getValue());
        // 字节重建通道内的裸非 ASCII 字符按 UTF-8 写出，与转义字节混排往返无损
        assertEquals("中中x", pair("/s?q=中%E4%B8%ADx", "q").getValue());
    }

    @Test
    void rawNonAsciiWithoutEscapesPassesThrough() {
        assertEquals("中文", pair("/s?q=中文", "q").getValue());
    }

    @Test
    void fragmentIsCutAtFirstHash() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("/s?q=1#frag");
        assertEquals(1, params.size());
        assertEquals("1", params.get(0).getValue());
        // 片段内的 = 与 & 不参与解析
        assertEquals(1, UrlHelper.parseQueryParams("/s?q=1#a=b&c=2").size());
    }

    @Test
    void queryStartsAtFirstQuestionMark() {
        // 第二个 ? 属于查询文本
        UrlHelper.ParamPair only = UrlHelper.parseQueryParams("a?b?c").get(0);
        assertEquals("b?c", only.getName());
        assertNull(only.getValue());
        assertEquals("1?y", pair("http://h/p?x=1?y", "x").getValue());
    }

    @Test
    void absoluteUriParsedLikeRelative() {
        assertEquals("1", pair("http://host:8080/p?x=1", "x").getValue());
    }

    @Test
    @DisplayName("畸形百分号转义：抛 IllegalArgumentException 由调用方兜底")
    void malformedEscapeThrows() {
        // 转义位后不是十六进制数字
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.parseQueryParams("/s?q=%zz"));
        // 尾部残缺："%" 后不足两位
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.parseQueryParams("/s?q=%2"));
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.parseQueryParams("/s?q=%"));
        // 半个合法序列混在合法转义后
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.parseQueryParams("/s?q=a%20b%"));
        // 名称与取值各自独立解码，坏取值炸、好名称不受影响
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.parseQueryParams("/s?%zz=1"));
    }

    @Test
    @DisplayName("十六进制零位是合法转义：%00 解码为 NUL 字符")
    void zeroHexDigitsAreValidEscapes() {
        // 高四位或低四位任一误判为非法（boundary 0 与负数混同）都会把 %00 炸成异常
        assertEquals("\u0000", pair("/s?q=%00", "q").getValue());
    }

    @Test
    @DisplayName("U+0080 恰好越界 ASCII：走多字节通道按 UTF-8 往返")
    void latinOneSupplementBoundaryCharRoundTrips() {
        assertEquals(" \u0080", pair("/s?q=%20\u0080", "q").getValue());
    }

    @Test
    void leadingQuestionMarkYieldsSingleParam() {
        List<UrlHelper.ParamPair> params = UrlHelper.parseQueryParams("?a=1");
        assertEquals(1, params.size());
        assertEquals("a", params.get(0).getName());
    }

    @Test
    void percentEncodedDelimiterBytesStayDecodedAsLiteralChars() {
        // %26(&) 与 %3D(=) 解码后是普通文本，不会被再次切分
        assertEquals("a&b=c", pair("/s?q=a%26b%3Dc", "q").getValue());
    }

    @Test
    @DisplayName("显式字符集为 null：拒绝")
    void nullCharsetRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> UrlHelper.parseQueryParams("/s?a=1", null));
        assertEquals("charset", e.getMessage());
    }

    @Test
    @DisplayName("显式传 UTF-8 与默认通道逐字一致")
    void explicitUtf8MatchesDefaultChannel() {
        assertEquals("中", pair("/s?q=%E4%B8%AD", "q", StandardCharsets.UTF_8).getValue());
        assertEquals(pair("/s?q=a%E4%B8%ADb+c", "q").getValue(),
                pair("/s?q=a%E4%B8%ADb+c", "q", StandardCharsets.UTF_8).getValue());
    }

    @Test
    @DisplayName("GBK 通道：%XX 按显式字符集还原，名称与取值同通道")
    void gbkEscapesDecodeUnderExplicitCharset() {
        UrlHelper.ParamPair p = pair("/s?q%D6%D0=%D6%D0", "q中", Charset.forName("GBK"));
        assertEquals("中", p.getValue());
    }

    @Test
    @DisplayName("ISO-8859-1 通道：%FF 还原为 U+00FF，不与 UTF-8 解释混同")
    void latinOneEscapesDecodeUnderExplicitCharset() {
        assertEquals("ÿ", pair("/s?q=%FF", "q", StandardCharsets.ISO_8859_1).getValue());
        assertEquals("aäb", pair("/s?q=a%E4b", "q", StandardCharsets.ISO_8859_1).getValue());
    }

    @Test
    @DisplayName("显式字符集下裸非 ASCII 字符按该字符集入字节再回读")
    void rawNonAsciiRoundTripsUnderGbkMixedWithEscapes() {
        assertEquals("中中x", pair("/s?q=%D6%D0中x", "q", Charset.forName("GBK")).getValue());
    }

    @Test
    @DisplayName("不可映射的裸字符按字符集编码替换为问号")
    void unmappableRawCharReplacedUnderLatinOne() {
        // "中" 无 ISO-8859-1 字节对应，编码替换为 ?；%20 走同一字节通道
        assertEquals(" ?", pair("/s?q=%20中", "q", StandardCharsets.ISO_8859_1).getValue());
    }

    @Nested
    @DisplayName("ParamPair 配对载体")
    class ParamPairTests {

        @Test
        void keepsNameAndValue() {
            UrlHelper.ParamPair pair = new UrlHelper.ParamPair("key", "val");
            assertEquals("key", pair.getName());
            assertEquals("val", pair.getValue());
        }

        @Test
        void keepsNullValueForBareSegment() {
            UrlHelper.ParamPair pair = new UrlHelper.ParamPair("flag", null);
            assertEquals("flag", pair.getName());
            assertNull(pair.getValue());
        }

        @Test
        void toStringRendersNameEqualsValue() {
            assertEquals("a=b", new UrlHelper.ParamPair("a", "b").toString());
            assertEquals("a=null", new UrlHelper.ParamPair("a", null).toString());
        }
    }
}
