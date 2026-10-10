package top.turboweb.http.core.helper;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 请求侧工具：Content-Type 取值与 charset 解析。
 */
class HttpRequestHelperTest {

    private static FullHttpRequest requestWithContentType(String contentType) {
        // 非校验头容器（DefaultHttpHeaders(false)）：netty 的校验器拒收以空白开头的头值
        // （prohibited character 0x20），而空白形态（""、" "）正是要直接喂给工具函数的输入，
        // 工具函数本身不经过 netty 校验，所以测试用关掉校验的构造方式还原真实取值路径。
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/probe",
                Unpooled.EMPTY_BUFFER, new DefaultHttpHeaders(false), new DefaultHttpHeaders(false));
        if (contentType != null) {
            req.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        return req;
    }

    @Test
    void contentTypeIsThePartBeforeSemicolon() {
        assertEquals("application/json",
                HttpRequestHelper.getContentType(requestWithContentType("application/json; charset=UTF-8")));
    }

    @Test
    void missingContentTypeStaysNull() {
        assertNull(HttpRequestHelper.getContentType(requestWithContentType(null)));
    }

    @Test
    @DisplayName("F12 回归：空白 Content-Type 归一为 null（与 getRequestCharset 的 blank-视同缺失口径一致）")
    void blankContentTypeIsNormalizedToNull() {
        assertNull(HttpRequestHelper.getContentType(requestWithContentType(" ")));
        assertNull(HttpRequestHelper.getContentType(requestWithContentType("")));
    }

    @Test
    void charsetComesFromContentTypeToken() {
        assertEquals(StandardCharsets.UTF_8,
                HttpRequestHelper.getRequestCharset(requestWithContentType("application/json; charset=UTF-8")));
        assertEquals(Charset.forName("GBK"),
                HttpRequestHelper.getRequestCharset(requestWithContentType("text/plain;charset=GBK")));
        // 大小写不敏感的 token 名，值仍按原样取
        assertEquals(StandardCharsets.UTF_8,
                HttpRequestHelper.getRequestCharset(requestWithContentType("application/json; CHARSET=UTF-8")));
    }

    @Test
    void emptyCharsetTokenIsSkippedAndUnparsableNameFallsThrough() {
        // "charset=" 空值走 continue，非法名走 catch，两者都该落到后面的部分或默认值
        assertEquals(StandardCharsets.UTF_16,
                HttpRequestHelper.getRequestCharset(requestWithContentType("a/b; charset=; charset=no-such-cs; charset=UTF-16")));
        assertEquals(StandardCharsets.UTF_8,
                HttpRequestHelper.getRequestCharset(requestWithContentType("a/b; charset=not-a-real-charset")));
    }

    @Test
    void nullRequestUsesGlobalConfigDefault() {
        Charset original = GlobalConfig.getRequestCharset();
        try {
            GlobalConfig.setRequestCharset(StandardCharsets.ISO_8859_1);
            assertEquals(StandardCharsets.ISO_8859_1, HttpRequestHelper.getRequestCharset(null),
                    "request 为 null 时必须回落到 GlobalConfig，而不是硬编码 UTF-8");
            // 顺带证明无 header 的 request 也走同一回落
            assertEquals(StandardCharsets.ISO_8859_1,
                    HttpRequestHelper.getRequestCharset(requestWithContentType(null)));
        } finally {
            GlobalConfig.setRequestCharset(original);
        }
        assertEquals(original, GlobalConfig.getRequestCharset(), "必须还原，否则污染其它用例");
    }
}
