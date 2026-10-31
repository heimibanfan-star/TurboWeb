package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JsonContentParser} 的 JSON 请求体解析：Content-Type 校验、空体补默认对象、
 * 花括号口径与解码字符集的头声明优先回落缺省。
 */
class JsonContentParserTest {

    private final JsonContentParser parser = new JsonContentParser();

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> parser.parse(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        FullHttpRequest req = Requests.postJson("/x", "{}");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> parser.parse(req, null));
            assertEquals("defaultCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonBodyIsParsedToString() {
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            assertEquals("{\"a\":1}", parser.parse(req, StandardCharsets.UTF_8));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        FullHttpRequest req = Requests.postJson("/x", "  {\"a\":1}  ");
        try {
            assertEquals("{\"a\":1}", parser.parse(req, StandardCharsets.UTF_8));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void blankBodyBecomesEmptyObject() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", Requests.JSON_CONTENT_TYPE, "   ",
                StandardCharsets.UTF_8);
        try {
            assertEquals("{}", parser.parse(req, StandardCharsets.UTF_8));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nonJsonContentTypeIsRejected() {
        FullHttpRequest form = Requests.postForm("/x", "a=1");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> parser.parse(form, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/json", ex.getMessage());
        } finally {
            Requests.release(form);
        }
    }

    @Test
    void missingContentTypeIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> parser.parse(req, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/json", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void bodyNotWrappedInObjectBracesIsRejected() {
        FullHttpRequest plain = Requests.postJson("/x", "not-a-json");
        FullHttpRequest array = Requests.postJson("/x", "[1,2]");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> parser.parse(plain, StandardCharsets.UTF_8));
            assertEquals("The request body is not a valid json", ex.getMessage());
            assertThrows(RequestParseException.class, () -> parser.parse(array, StandardCharsets.UTF_8),
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
            assertEquals("{\"n\":\"张三\"}", parser.parse(req, StandardCharsets.UTF_8));
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
            assertEquals("{\"n\":\"张三\"}", parser.parse(req, Charset.forName("GBK")));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void parsedStringSurvivesAfterRequestRelease() {
        // 解析期已完成字节到字符串的复制，请求对象随即可被调用方释放
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        String jsonString = (String) parser.parse(req, StandardCharsets.UTF_8);
        Requests.release(req);
        assertEquals("{\"a\":1}", jsonString);
    }

    @Test
    void parseReturnsTheStringItself() {
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            Object content = parser.parse(req, StandardCharsets.UTF_8);
            assertSame(String.class, content.getClass(), "JSON 解析产出即字符串本身，无包装内容类");
        } finally {
            Requests.release(req);
        }
    }
}
