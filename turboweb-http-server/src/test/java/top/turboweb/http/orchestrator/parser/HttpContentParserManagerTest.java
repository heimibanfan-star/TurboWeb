package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpContentParserManager} 的内容类型路由：内置解析器优先、自定义解析器次之、
 * 无匹配回落 {@code null}；以及构造期注册校验口径。
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
        public Object parse(FullHttpRequest request, Charset defaultCharset) {
            this.seenCharset = defaultCharset;
            return new MarkerContent();
        }
    }

    private static final class MarkerContent {
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
    void urlencodedRequestRoutesToFormContent() {
        FullHttpRequest req = Requests.postForm("/x", "name=alice&tag=a&tag=b");
        try {
            Object content = manager().parse(req);
            FormRequestMessage.FormData form = assertInstanceOf(FormRequestMessage.FormData.class, content);
            assertEquals(List.of("alice"), form.formParams().get("name"));
            assertEquals(List.of("a", "b"), form.formParams().get("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multipartRequestRoutesToFormContent() {
        FullHttpRequest req = multipart();
        try {
            Object content = manager().parse(req);
            FormRequestMessage.MultipartFormData form =
                    assertInstanceOf(FormRequestMessage.MultipartFormData.class, content);
            List<MultipartFile> docs = form.formFiles().get("doc");
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
            Object content = manager().parse(req);
            String json = assertInstanceOf(String.class, content);
            assertEquals("{\"a\":1}", json);
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void builtinContentsAreTheChannelTypes() {
        FullHttpRequest formReq = Requests.postForm("/x", "k=v");
        FullHttpRequest multipartReq = multipart();
        FullHttpRequest jsonReq = Requests.postJson("/x", "{}");
        try {
            assertInstanceOf(FormRequestMessage.FormData.class, manager().parse(formReq),
                    "表单渠道就是内置表单内容类，取数方按具体类认领");
            FormRequestMessage.MultipartFormData multipart = assertInstanceOf(
                    FormRequestMessage.MultipartFormData.class, manager().parse(multipartReq));
            assertInstanceOf(AutoCloseable.class, multipart,
                    "持有解码器的多部分表单内容实现 AutoCloseable 进入统一关闭通道");
            multipart.close();
            assertInstanceOf(String.class, manager().parse(jsonReq),
                    "JSON 渠道产出内置解析器解析的字符串");
        } finally {
            Requests.release(formReq);
            Requests.release(multipartReq);
            Requests.release(jsonReq);
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
            assertEquals("{\"n\":\"张三\"}", assertInstanceOf(String.class, gbkManager.parse(req)));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void unsupportedDeclaredTypeFallsBackToNull() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "text/plain; charset=UTF-8",
                "hello", StandardCharsets.UTF_8);
        try {
            assertNull(manager().parse(req), "无匹配解析器的声明类型回落 null，编码段不参与路由");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void missingContentTypeFallsBackToNull() {
        FullHttpRequest req = Requests.get("/x");
        try {
            assertNull(manager().parse(req), "无 Content-Type 头时回落 null");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void builtinMatchingIgnoresCase() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x", "APPLICATION/JSON",
                "{\"a\":1}", StandardCharsets.UTF_8);
        try {
            assertInstanceOf(String.class, manager().parse(req));
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
