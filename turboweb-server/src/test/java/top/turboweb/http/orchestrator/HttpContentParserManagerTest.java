package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpFormContent;
import top.turboweb.http.context.HttpJsonContent;
import top.turboweb.http.context.HttpUnknowContent;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpContentParserManager} 的内容类型路由：内置解析器优先、自定义解析器次之、
 * 无匹配回落未知内容；以及构造期注册校验口径。
 */
class HttpContentParserManagerTest {

    /** multipart 夹具：一个普通字段 k 与一个文件字段 doc（与表单内容用例同形）。 */
    private static final String MULTIPART_BODY =
            "--T\r\nContent-Disposition: form-data; name=\"k\"\r\n\r\nv\r\n"
                    + "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                    + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n";

    private static FullHttpRequest multipart() {
        return Requests.raw(HttpMethod.POST, "/up", "multipart/form-data; boundary=T",
                MULTIPART_BODY, StandardCharsets.UTF_8);
    }

    private static HttpContentParserManager manager() {
        return new HttpContentParserManager(StandardCharsets.UTF_8, List.of());
    }

    /** 自定义解析器测试桩：记录收到的缺省字符集，返回标记内容以证实路由落点。 */
    private static final class MarkerParser implements HttpContentParser {
        private final Set<ContentType> types;
        private Charset seenCharset;

        MarkerParser(String mimeType) {
            this.types = Set.of(ContentType.create(mimeType));
        }

        @Override
        public Set<ContentType> contentTypes() {
            return types;
        }

        @Override
        public HttpContext.Content parse(FullHttpRequest request, Charset defaultCharset) {
            this.seenCharset = defaultCharset;
            return new MarkerContent();
        }
    }

    private static final class MarkerContent implements HttpContext.Content {
        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpContentParserManager(null, List.of()));
        assertEquals("defaultCharset can not be null", ex.getMessage());
    }

    @Test
    void nullCustomParsersIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpContentParserManager(StandardCharsets.UTF_8, null));
        assertEquals("customParsers can not be null", ex.getMessage());
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> manager().parse(null));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void urlencodedRequestRoutesToFormContent() throws Exception {
        FullHttpRequest req = Requests.postForm("/x", "name=alice&tag=a&tag=b");
        try {
            HttpContext.Content content = manager().parse(req);
            HttpFormContent form = assertInstanceOf(HttpFormContent.class, content);
            assertEquals(List.of("alice"), form.formParams().get("name"));
            assertEquals(List.of("a", "b"), form.formParams().get("tag"));
            assertSame(ContentType.APPLICATION_FORM_URLENCODED, form.contentType());
            form.close();
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multipartRequestRoutesToFormContent() throws Exception {
        FullHttpRequest req = multipart();
        try {
            HttpContext.Content content = manager().parse(req);
            HttpFormContent form = assertInstanceOf(HttpFormContent.class, content);
            assertSame(ContentType.MULTIPART_FORM_DATA, form.contentType());
            List<FileUpload> docs = form.formFiles().get("doc");
            assertEquals(1, docs.size(), "multipart 与 urlencoded 同由表单解析器承接");
            form.close();
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonRequestRoutesToJsonContent() {
        FullHttpRequest req = Requests.postJson("/x", "{\"a\":1}");
        try {
            HttpContext.Content content = manager().parse(req);
            HttpJsonContent json = assertInstanceOf(HttpJsonContent.class, content);
            assertEquals("{\"a\":1}", json.jsonString());
            assertSame(ContentType.APPLICATION_JSON, json.contentType());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonDecodedWithManagerDefaultCharsetWhenHeaderOmitsIt() {
        // 头不带 charset 段：体字节按 GBK 写入，管理器缺省字符集给 GBK，解析须按缺省解码
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/json",
                "{\"n\":\"张三\"}", Charset.forName("GBK"));
        try {
            HttpContentParserManager gbkManager =
                    new HttpContentParserManager(Charset.forName("GBK"), List.of());
            HttpJsonContent json = assertInstanceOf(HttpJsonContent.class, gbkManager.parse(req));
            assertEquals("{\"n\":\"张三\"}", json.jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void unsupportedDeclaredTypeBecomesUnknownContent() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "text/plain; charset=UTF-8",
                "hello", StandardCharsets.UTF_8);
        try {
            HttpContext.Content content = manager().parse(req);
            HttpUnknowContent unknow = assertInstanceOf(HttpUnknowContent.class, content);
            assertEquals("text/plain", unknow.contentType().getMimeType(),
                    "未知内容携带头声明的类型，编码段不参与路由");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void missingContentTypeBecomesUnknownContent() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpUnknowContent unknow = assertInstanceOf(HttpUnknowContent.class, manager().parse(req));
            assertNull(unknow.contentType(), "无 Content-Type 头时未知内容不携类型");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void builtinMatchingIgnoresCase() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "APPLICATION/JSON",
                "{\"a\":1}", StandardCharsets.UTF_8);
        try {
            assertInstanceOf(HttpJsonContent.class, manager().parse(req));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void customParserRoutesByNormalizedType() {
        MarkerParser parser = new MarkerParser("Application/XML");
        HttpContentParserManager manager =
                new HttpContentParserManager(StandardCharsets.UTF_8, List.of(parser));
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "APPLICATION/XML",
                "<a/>", StandardCharsets.UTF_8);
        try {
            assertInstanceOf(MarkerContent.class, manager.parse(req),
                    "注册键与请求头均按转小写归一后匹配");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void customParserReceivesManagerDefaultCharset() {
        MarkerParser parser = new MarkerParser("application/xml");
        HttpContentParserManager manager =
                new HttpContentParserManager(Charset.forName("GBK"), List.of(parser));
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "application/xml",
                "<a/>", StandardCharsets.UTF_8);
        try {
            manager.parse(req);
            assertEquals(Charset.forName("GBK"), parser.seenCharset,
                    "管理器把装配期缺省字符集透传给自定义解析器");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void customParserConflictingWithBuiltinIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new HttpContentParserManager(StandardCharsets.UTF_8,
                        List.of(new MarkerParser("application/json"))));
        assertEquals("custom parser content type application/json conflicts with builtin parser",
                ex.getMessage());
    }

    @Test
    void duplicateCustomParsersAreRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new HttpContentParserManager(StandardCharsets.UTF_8,
                        List.of(new MarkerParser("application/xml"), new MarkerParser("APPLICATION/XML"))));
        assertEquals("duplicate custom parser for content type application/xml", ex.getMessage());
    }

    @Test
    void invalidJsonBodyPropagatesParseException() {
        FullHttpRequest req = Requests.postJson("/x", "not-a-json");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class, () -> manager().parse(req));
            assertEquals("The request body is not a valid json", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

}
