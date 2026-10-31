package top.turboweb.http.request;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JsonRequestMessage} 与其 {@link JsonRequestMessage.JsonData} 内容类的
 * JSON 请求体构造期自解析与纯持有语义。
 */
class JsonRequestMessageTest {

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new JsonRequestMessage.JsonData(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        FullHttpRequest req = Requests.postJson("/x", "{}");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new JsonRequestMessage.JsonData(req, null));
            assertEquals("defaultCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonBodyIsParsedOnConstruction() {
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            JsonRequestMessage.JsonData content = new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8);
            assertEquals("{\"a\":1}", content.jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        FullHttpRequest req = Requests.postJson("/x", "  {\"a\":1}  ");
        try {
            assertEquals("{\"a\":1}", new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void blankBodyBecomesEmptyObject() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", Requests.JSON_CONTENT_TYPE, "   ",
                StandardCharsets.UTF_8);
        try {
            assertEquals("{}", new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nonJsonContentTypeIsRejectedOnConstruction() {
        FullHttpRequest form = Requests.postForm("/x", "a=1");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> new JsonRequestMessage.JsonData(form, StandardCharsets.UTF_8));
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
                    () -> new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8));
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
                    () -> new JsonRequestMessage.JsonData(plain, StandardCharsets.UTF_8));
            assertEquals("The request body is not a valid json", ex.getMessage());
            assertThrows(RequestParseException.class, () -> new JsonRequestMessage.JsonData(array, StandardCharsets.UTF_8),
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
            assertEquals("{\"n\":\"张三\"}", new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8).jsonString());
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
            assertEquals("{\"n\":\"张三\"}", new JsonRequestMessage.JsonData(req, Charset.forName("GBK")).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void stringSurvivesAfterRequestRelease() {
        // 构造期已完成字节到字符串的复制，请求对象随即可被调用方释放
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        JsonRequestMessage.JsonData content = new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8);
        Requests.release(req);
        assertEquals("{\"a\":1}", content.jsonString());
    }

    @Test
    void messageCarriesInjectedLineAndTypedContent() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            JsonRequestMessage.JsonData content = new JsonRequestMessage.JsonData(req, StandardCharsets.UTF_8);
            JsonRequestMessage message = new JsonRequestMessage(line, content);
            assertSame(line, message.requestLine(), "请求行按入参原样持有");
            assertSame(content, message.content(), "内容按入参原样持有且类型为 JsonData");
        } finally {
            Requests.release(req);
        }
    }
}
