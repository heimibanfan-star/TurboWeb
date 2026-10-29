package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RequestTarget} 的三通道口径：路径剥离查询参数与结尾斜杠、
 * 目标原文原样回放、查询参数按构造字符集解码并按名分组。
 */
class RequestTargetTest {

    @Test
    void pathStripsQueryAndTrailingSlash() {
        assertEquals("/a/b", new RequestTarget("/a/b/?x=1").path(), "路径不带查询参数，结尾斜杠剥离");
        assertEquals("/a/b", new RequestTarget("/a/b?x=1").path(), "本就无结尾斜杠的路径原样返回");
    }

    @Test
    void pathKeepsRootSlash() {
        assertEquals("/", new RequestTarget("/").path(), "根路径保持斜杠，避免归一为空串");
        assertEquals("/", new RequestTarget("/?x=1").path(), "根路径带查询参数时同样保持斜杠");
    }

    @Test
    void pathCutsFragment() {
        assertEquals("/a", new RequestTarget("/a/#f?x=1").path(), "片段段同样不计入路径");
    }

    @Test
    void rawReturnsOriginalString() {
        assertEquals("/x?y=1#f", new RequestTarget("/x?y=1#f").raw(), "目标原文原样回放，不做任何改写");
    }

    @Test
    void queryParamsAreDecodedAndGrouped() {
        Map<String, List<String>> params = new RequestTarget("/x?a=1&a=2&b=%E4%B8%AD&flag").queryParams();

        assertEquals(List.of("1", "2"), params.get("a"), "同名参数按出现顺序保留");
        assertEquals(List.of("中"), params.get("b"), "缺省按 UTF-8 解码百分号转义");
        assertEquals(1, params.get("flag").size(), "无等号段按单值参数分组");
        assertNull(params.get("flag").get(0), "无等号段取值为 null");
    }

    @Test
    void queryParamsDecodeWithConfiguredCharset() {
        Map<String, List<String>> params =
                new RequestTarget("/x?n=%D6%D0", Charset.forName("GBK")).queryParams();

        assertEquals(List.of("中"), params.get("n"), "百分号转义按构造给定的字符集解码");
    }

    @Test
    void queryParamsEmptyWhenTargetHasNoQuery() {
        assertTrue(new RequestTarget("/x").queryParams().isEmpty(), "无查询段返回空 Map");
        assertNull(new RequestTarget("/x").queryParams().get("a"), "无查询段取不到任何参数");
    }

    @Test
    void nullRawIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new RequestTarget(null));

        assertEquals("raw must not be null", e.getMessage());
    }

    @Test
    void nullCharsetIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new RequestTarget("/x", (Charset) null));

        assertEquals("charset must not be null", e.getMessage());
    }
}
