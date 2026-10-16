package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultMessage} 的组装口径：头视图与地址对象的构造期建立、实例稳定性与缺省字符集透传。
 */
class DefaultMessageTest {

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DefaultMessage(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullQueryCharsetIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new DefaultMessage(req, null));
            assertEquals("queryCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void headersExposesRequestHeaders() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/json; charset=UTF-8",
                "{}", StandardCharsets.UTF_8);
        try {
            req.headers().set("Host", "example.com");
            DefaultMessage message = new DefaultMessage(req, StandardCharsets.UTF_8);
            assertEquals("application/json; charset=UTF-8", message.headers().contentType());
            assertEquals("example.com", message.headers().host());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void urlExposesUriAndDecodedQueryParams() {
        FullHttpRequest req = Requests.get("/api/search?q=hello&tag=a&tag=b");
        try {
            DefaultMessage message = new DefaultMessage(req, StandardCharsets.UTF_8);
            assertEquals("/api/search?q=hello&tag=a&tag=b", message.url().url(false));
            assertEquals("/api/search", message.url().url(true));
            assertEquals(List.of("hello"), message.url().queryParams().get("q"));
            assertEquals(List.of("a", "b"), message.url().queryParams().get("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryCharsetPlumbedToUrlDecoding() {
        FullHttpRequest req = Requests.get("/x?n=%D6%D0");
        try {
            DefaultMessage message = new DefaultMessage(req, Charset.forName("GBK"));
            assertEquals("中", message.url().queryParams().get("n").get(0),
                    "装配期缺省字符集透传至地址对象的查询解码");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void accessorsReturnStableInstances() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = new DefaultMessage(req, StandardCharsets.UTF_8);
            assertSame(message.headers(), message.headers(), "视图构造期建立，重复访问同实例");
            assertSame(message.url(), message.url());
        } finally {
            Requests.release(req);
        }
    }
}
