package top.turboweb.http.context.content;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboHttpParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpContent} 的请求体解析（spec §5：context 行）。
 */
class HttpContentTest {

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new HttpContent(null));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void contentTypeIsTheHeaderWithoutParameters() {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest plain = Requests.raw(HttpMethod.POST, "/x", "text/plain; charset=GBK", "hi", Charset.forName("GBK"));
        FullHttpRequest none = Requests.get("/x");
        try {
            assertEquals("application/json", new HttpContent(json).getContentType());
            assertEquals("text/plain", new HttpContent(plain).getContentType());
            assertNull(new HttpContent(none).getContentType());
        } finally {
            Requests.release(json);
            Requests.release(plain);
            Requests.release(none);
        }
    }

    @Test
    void jsonBodyIsTrimmedAndCached() {
        FullHttpRequest req = Requests.postJson("/x", "  {\"a\":1}  ");
        try {
            HttpContent content = new HttpContent(req);
            assertEquals("{\"a\":1}", content.getJsonContent());
            assertSame(content.getJsonContent(), content.getJsonContent(), "第二次读取命中缓存，返回同一字符串");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void blankJsonBodyBecomesEmptyObject() {
        FullHttpRequest req = Requests.postJson("/x", "");
        try {
            assertEquals("{}", new HttpContent(req).getJsonContent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void bodyWithoutOuterBracesIsRejected() {
        FullHttpRequest plain = Requests.postJson("/x", "abc");
        FullHttpRequest unclosed = Requests.postJson("/x", "{\"a\":1");
        try {
            TurboHttpParseException ex =
                    assertThrows(TurboHttpParseException.class, () -> new HttpContent(plain).getJsonContent());
            assertEquals("The request body is not a valid json", ex.getMessage());
            assertThrows(TurboHttpParseException.class, () -> new HttpContent(unclosed).getJsonContent());
        } finally {
            Requests.release(plain);
            Requests.release(unclosed);
        }
    }

    @Test
    void jsonIsRejectedWhenContentTypeIsNotJson() {
        FullHttpRequest none = Requests.get("/x");
        FullHttpRequest text = Requests.raw(HttpMethod.POST, "/x", "text/plain", "hi", Charset.forName("UTF-8"));
        try {
            TurboHttpParseException ex =
                    assertThrows(TurboHttpParseException.class, () -> new HttpContent(none).getJsonContent());
            assertEquals("contentType is not application/json", ex.getMessage());
            assertThrows(TurboHttpParseException.class, () -> new HttpContent(text).getJsonContent());
        } finally {
            Requests.release(none);
            Requests.release(text);
        }
    }

    @Test
    void jsonCharsetComesFromTheContentTypeHeader() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x",
                "application/json; charset=GBK", "{\"n\":\"张三\"}", Charset.forName("GBK"));
        try {
            assertEquals("{\"n\":\"张三\"}", new HttpContent(req).getJsonContent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonCharsetWithoutHeaderFallsBackToGlobalConfig() {
        // 先读当前值再编码，不用例内改写 GlobalConfig
        Charset charset = GlobalConfig.getRequestCharset();
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/json", "{\"n\":\"café\"}", charset);
        try {
            assertEquals("{\"n\":\"café\"}", new HttpContent(req).getJsonContent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void formParamsAreParsedForUrlEncoded() {
        FullHttpRequest req = Requests.postForm("/x", "name=alice&age=30&tag=a&tag=b");
        try {
            Map<String, List<String>> params = new HttpContent(req).getFormParams();
            assertEquals(List.of("alice"), params.get("name"));
            assertEquals(List.of("30"), params.get("age"));
            assertEquals(List.of("a", "b"), params.get("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void formParamsAreCachedAcrossCalls() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            HttpContent content = new HttpContent(req);
            assertSame(content.getFormParams(), content.getFormParams());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void formParamsAreRejectedForOtherContentTypes() {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest none = Requests.get("/x");
        try {
            TurboHttpParseException ex =
                    assertThrows(TurboHttpParseException.class, () -> new HttpContent(json).getFormParams());
            assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data", ex.getMessage());
            assertThrows(TurboHttpParseException.class, () -> new HttpContent(none).getFormParams());
        } finally {
            Requests.release(json);
            Requests.release(none);
        }
    }

    @Test
    void formFilesAreRejectedForNonMultipart() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            TurboHttpParseException ex =
                    assertThrows(TurboHttpParseException.class, () -> new HttpContent(req).getFormFiles());
            assertEquals("contentType is not multipart/form-data", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void emptyContentHasNullTypeAndRejectsEveryBodyAccess() {
        HttpContent content = HttpContent.empty();
        assertNull(content.getContentType());
        assertThrows(TurboHttpParseException.class, content::getJsonContent);
        assertThrows(TurboHttpParseException.class, content::getFormParams);
        assertThrows(TurboHttpParseException.class, content::getFormFiles);
        assertDoesNotThrow(content::release, "decoder 为 null，release 无副作用");
    }

    @Test
    void releaseBeforeParsingIsRepeatable() {
        FullHttpRequest req = Requests.postJson("/x", "{}");
        try {
            HttpContent content = new HttpContent(req);
            assertDoesNotThrow(() -> {
                content.release();
                content.release();
            });
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void releaseAfterFormParsingDestroysTheDecoderOnce() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            HttpContent content = new HttpContent(req);
            content.getFormParams();
            assertDoesNotThrow(content::release);
        } finally {
            Requests.release(req);
        }
    }

    @Test
    @DisplayName("钉住 HS-5：解析过表单后 release 不幂等，二次调用抛 IllegalStateException")
    void releaseAfterFormParsingIsNotRepeatable() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            HttpContent content = new HttpContent(req);
            content.getFormParams();
            content.release();
            // 当前实现：HttpContent.decoder 销毁后不清空，第二次 destroy() 命中
            // HttpPostStandardRequestDecoder.checkDestroyed()。
            // 钉住 HS-5：若将来修正，本用例应改为期望正确行为（若将来把 release 改成幂等，本用例应改为 assertDoesNotThrow）。
            assertThrows(IllegalStateException.class, content::release);
        } finally {
            Requests.release(req);
        }
    }
}
