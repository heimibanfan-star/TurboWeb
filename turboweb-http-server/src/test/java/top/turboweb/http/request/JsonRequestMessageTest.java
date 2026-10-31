package top.turboweb.http.request;

import org.junit.jupiter.api.Test;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JsonRequestMessage} 的纯持有语义：JSON 体字符串由解析器解析产出，
 * 消息只按入参原样持有请求行与字符串。
 */
class JsonRequestMessageTest {

    @Test
    void nullLineIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new JsonRequestMessage(null, "{}"));
        assertEquals("line must not be null", ex.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new JsonRequestMessage(line, null));
        assertEquals("content must not be null", ex.getMessage());
    }

    @Test
    void messageCarriesInjectedLineAndJsonString() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        JsonRequestMessage message = new JsonRequestMessage(line, "{\"a\":1}");
        assertSame(line, message.requestLine(), "请求行按入参原样持有");
        assertSame("{\"a\":1}", message.content(), "JSON 体字符串按入参原样持有");
        assertSame(message.content(), message.jsonString(), "jsonString 即消息内容本身");
        assertEquals("{\"a\":1}", message.jsonString());
    }

    @Test
    void emptyObjectStringIsHeldVerbatim() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        JsonRequestMessage message = new JsonRequestMessage(line, "{}");
        assertEquals("{}", message.jsonString());
    }
}
