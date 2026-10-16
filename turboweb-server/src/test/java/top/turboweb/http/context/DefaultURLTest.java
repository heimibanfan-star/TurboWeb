package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultURL} 的请求地址取用与查询参数解码：路径截断口径、百分号解码字符集、
 * 懒建缓存与不可变视图。
 */
class DefaultURLTest {

    private static DefaultURL urlOf(String uri, Charset queryCharset) {
        FullHttpRequest req = Requests.get(uri);
        try {
            return new DefaultURL(req, queryCharset);
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DefaultURL(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullQueryCharsetIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new DefaultURL(req, null));
            assertEquals("queryCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void rawUriReturnedVerbatim() {
        DefaultURL url = urlOf("/x?a=1&b=2#frag", StandardCharsets.UTF_8);
        assertEquals("/x?a=1&b=2#frag", url.url(false), "false 返回请求行原始地址");
    }

    @Test
    void pathOnlyStripsQueryAndFragment() {
        assertEquals("/x", urlOf("/x?a=1&b=2#frag", StandardCharsets.UTF_8).url(true));
        assertEquals("/x", urlOf("/x#frag", StandardCharsets.UTF_8).url(true),
                "片段段一并截除，与查询参数解码口径一致");
    }

    @Test
    void pathOnlyEqualsUriWhenNoQueryOrFragment() {
        assertEquals("/x/y", urlOf("/x/y", StandardCharsets.UTF_8).url(true));
    }

    @Test
    void emptyOrMissingQueryYieldsEmptyMap() {
        assertTrue(urlOf("/x", StandardCharsets.UTF_8).queryParams().isEmpty());
        assertTrue(urlOf("/x?", StandardCharsets.UTF_8).queryParams().isEmpty(),
                "空查询串同样返回空映射");
    }

    @Test
    void multiValuesKeptInOrderWithKeyOrder() {
        Map<String, List<String>> params = urlOf("/x?a=1&b=2&a=3", StandardCharsets.UTF_8).queryParams();
        assertEquals(List.of("1", "3"), params.get("a"), "同名参数按出现顺序保留");
        assertEquals(List.of("2"), params.get("b"));
        assertEquals(List.of("a", "b"), new ArrayList<>(params.keySet()), "键按首现顺序");
    }

    @Test
    void valuelessSegmentKeepsNullAndEmptyValue() {
        Map<String, List<String>> params = urlOf("/x?flag&a=", StandardCharsets.UTF_8).queryParams();
        assertEquals(Arrays.asList((String) null), params.get("flag"), "无等号段取值为 null");
        assertEquals(List.of(""), params.get("a"), "有等号无取值段为空串");
    }

    @Test
    void percentDecodingUsesGivenCharset() {
        assertEquals("中", urlOf("/p?u=%E4%B8%AD", StandardCharsets.UTF_8).queryParams().get("u").get(0));
        assertEquals("中", urlOf("/p?g=%D6%D0", Charset.forName("GBK")).queryParams().get("g").get(0),
                "转义字节按传入缺省字符集组装");
    }

    @Test
    void malformedEscapePropagatesIllegalArgument() {
        DefaultURL url = urlOf("/x?a=%zz", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, url::queryParams,
                "畸形转义原样传播，由装配点决定处置");
    }

    @Test
    void queryParamsAreUnmodifiable() {
        Map<String, List<String>> params = urlOf("/x?a=1", StandardCharsets.UTF_8).queryParams();
        assertThrows(UnsupportedOperationException.class, () -> params.put("b", List.of("2")));
        assertThrows(UnsupportedOperationException.class, () -> params.get("a").add("2"));
    }

    @Test
    void queryParamsCachedAcrossCalls() {
        DefaultURL url = urlOf("/x?a=1", StandardCharsets.UTF_8);
        assertSame(url.queryParams(), url.queryParams(), "首访建立后缓存复用");
    }
}
