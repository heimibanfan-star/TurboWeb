package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpJsonContent} 的 JSON 请求体构造期自解析与纯持有语义。
 */
class HttpJsonContentTest {

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpJsonContent(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        FullHttpRequest req = Requests.postJson("/x", "{}");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> new HttpJsonContent(req, null));
            assertEquals("defaultCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonBodyIsParsedOnConstruction() {
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            HttpJsonContent content = new HttpJsonContent(req, StandardCharsets.UTF_8);
            assertEquals("{\"a\":1}", content.jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        FullHttpRequest req = Requests.postJson("/x", "  {\"a\":1}  ");
        try {
            assertEquals("{\"a\":1}", new HttpJsonContent(req, StandardCharsets.UTF_8).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void blankBodyBecomesEmptyObject() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", Requests.JSON_CONTENT_TYPE, "   ",
                StandardCharsets.UTF_8);
        try {
            assertEquals("{}", new HttpJsonContent(req, StandardCharsets.UTF_8).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nonJsonContentTypeIsRejectedOnConstruction() {
        FullHttpRequest form = Requests.postForm("/x", "a=1");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> new HttpJsonContent(form, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/json", ex.getMessage());
        } finally {
            Requests.release(form);
        }
    }

    @Test
    void missingContentTypeIsRejectedOnConstruction() {
        FullHttpRequest req = Requests.get("/x");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> new HttpJsonContent(req, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/json", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void bodyNotWrappedInObjectBracesIsRejectedOnConstruction() {
        FullHttpRequest plain = Requests.postJson("/x", "not-a-json");
        FullHttpRequest array = Requests.postJson("/x", "[1,2]");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> new HttpJsonContent(plain, StandardCharsets.UTF_8));
            assertEquals("The request body is not a valid json", ex.getMessage());
            assertThrows(RequestParseException.class, () -> new HttpJsonContent(array, StandardCharsets.UTF_8),
                    "数组体不在对象花括号口径内，同样拒绝");
        } finally {
            Requests.release(plain);
            Requests.release(array);
        }
    }

    @Test
    void jsonCharsetComesFromContentTypeHeader() {
        // 体字节按 GBK 写入，头声明 charset=GBK，缺省字符集给 UTF-8：取值须按头声明解码
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/json; charset=GBK",
                "{\"n\":\"张三\"}", Charset.forName("GBK"));
        try {
            assertEquals("{\"n\":\"张三\"}", new HttpJsonContent(req, StandardCharsets.UTF_8).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonCharsetFallsBackToGivenDefaultWhenHeaderOmitsIt() {
        // 头不带 charset 段：体字节按 GBK 写入，缺省字符集给 GBK，取值须按缺省解码
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/json",
                "{\"n\":\"张三\"}", Charset.forName("GBK"));
        try {
            assertEquals("{\"n\":\"张三\"}", new HttpJsonContent(req, Charset.forName("GBK")).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void stringSurvivesAfterRequestRelease() {
        // 构造期已完成字节到字符串的复制，请求对象随即可被调用方释放
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        HttpJsonContent content = new HttpJsonContent(req, StandardCharsets.UTF_8);
        Requests.release(req);
        assertEquals("{\"a\":1}", content.jsonString());
    }
}
