package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormContentParser} 的表单请求体解析：Content-Type 校验、字段与文件归档、
 * 解码字符集的头声明优先回落缺省，以及 urlencoded 与 multipart 的产出类型分野与释放归属。
 */
class FormContentParserTest {

    /** multipart 夹具：一个普通字段 k 与一个文件字段 doc。 */
    private static final String MULTIPART_BODY =
            "--T\r\nContent-Disposition: form-data; name=\"k\"\r\n\r\nv\r\n"
                    + "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                    + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n";

    private final FormContentParser parser = new FormContentParser();

    private static FullHttpRequest multipart() {
        return Requests.raw(HttpMethod.POST, "/up", "multipart/form-data; boundary=T",
                MULTIPART_BODY, StandardCharsets.UTF_8);
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> parser.parse(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> parser.parse(req, null));
            assertEquals("defaultCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void urlencodedBodyIsParsedToPlainFormData() {
        FullHttpRequest req = Requests.postForm("/x", "name=alice&age=30&tag=a&tag=b");
        try {
            FormRequestMessage.FormData content =
                    assertInstanceOf(FormRequestMessage.FormData.class, parser.parse(req, StandardCharsets.UTF_8));
            Map<String, List<String>> params = content.formParams();
            assertEquals(List.of("alice"), params.get("name"));
            assertEquals(List.of("30"), params.get("age"));
            assertEquals(List.of("a", "b"), params.get("tag"));
            assertFalse(content instanceof FormRequestMessage.MultipartFormData,
                    "urlencoded 请求不产出多部分内容");
            assertFalse(content instanceof AutoCloseable, "普通表单字段为堆上字符串，不背负关闭义务");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multipartBodyIsParsedToMultipartFormData() {
        FullHttpRequest req = multipart();
        try {
            FormRequestMessage.MultipartFormData content = assertInstanceOf(
                    FormRequestMessage.MultipartFormData.class, parser.parse(req, StandardCharsets.UTF_8));
            assertEquals(List.of("v"), content.formParams().get("k"));
            List<MultipartFile> docs = content.formFiles().get("doc");
            assertEquals(1, docs.size(), "multipart 文件段归档进 formFiles");
            assertEquals("a.txt", docs.getFirst().filename());
            content.close();
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nonFormContentTypeIsRejected() {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest none = Requests.get("/x");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> parser.parse(json, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data", ex.getMessage());
            assertThrows(RequestParseException.class, () -> parser.parse(none, StandardCharsets.UTF_8));
        } finally {
            Requests.release(json);
            Requests.release(none);
        }
    }

    @Test
    void formCharsetComesFromContentTypeHeader() {
        // 张三 的 GBK 百分号编码：%D5%C5 为张、%C8%FD 为三
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x",
                "application/x-www-form-urlencoded; charset=GBK", "n=%D5%C5%C8%FD", StandardCharsets.UTF_8);
        try {
            FormRequestMessage.FormData content =
                    assertInstanceOf(FormRequestMessage.FormData.class, parser.parse(req, StandardCharsets.UTF_8));
            assertEquals(List.of("张三"), content.formParams().get("n"), "解码按头声明的字符集，不受缺省字符集影响");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void formCharsetFallsBackToGivenDefaultWhenHeaderOmitsIt() {
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x",
                "application/x-www-form-urlencoded", "n=%D5%C5%C8%FD", StandardCharsets.UTF_8);
        try {
            FormRequestMessage.FormData content = assertInstanceOf(FormRequestMessage.FormData.class,
                    parser.parse(req, Charset.forName("GBK")));
            assertEquals(List.of("张三"), content.formParams().get("n"), "头不带 charset 段时回落到传入的缺省字符集");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void parsedMapsAreStableAcrossCalls() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            FormRequestMessage.FormData content =
                    assertInstanceOf(FormRequestMessage.FormData.class, parser.parse(req, StandardCharsets.UTF_8));
            assertSame(content.formParams(), content.formParams(), "解析结果一次成型，多次访问返回同一 Map");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void parsedDataAvailableAfterRequestRelease() {
        // 解析期请求体数据已归档为内容对象的持有物，请求对象可先于属性访问释放
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        FormRequestMessage.FormData content =
                assertInstanceOf(FormRequestMessage.FormData.class, parser.parse(req, StandardCharsets.UTF_8));
        Requests.release(req);
        assertEquals(List.of("1"), content.formParams().get("a"));
    }

    @Test
    void multipartDataAvailableAfterRequestRelease() {
        // 解析期请求体字节已转入解码器持有的数据对象，请求对象可先于文件读取释放
        FullHttpRequest req = multipart();
        FormRequestMessage.MultipartFormData content = assertInstanceOf(
                FormRequestMessage.MultipartFormData.class, parser.parse(req, StandardCharsets.UTF_8));
        Requests.release(req);
        assertEquals(1, content.formFiles().get("doc").size());
        content.close();
    }

    @Test
    void malformedUrlencodedBodyPropagatesDecoderFailure() {
        // %zz 非合法百分号编码，Netty 构造期解码即失败；钉住口径：异常原样传播
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x",
                "application/x-www-form-urlencoded", "n=%zz", StandardCharsets.UTF_8);
        try {
            assertThrows(HttpPostRequestDecoder.ErrorDataDecoderException.class,
                    () -> parser.parse(req, StandardCharsets.UTF_8));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multipartCloseIsIdempotent() {
        FullHttpRequest req = multipart();
        try {
            FormRequestMessage.MultipartFormData content = assertInstanceOf(
                    FormRequestMessage.MultipartFormData.class, parser.parse(req, StandardCharsets.UTF_8));
            content.close();
            assertDoesNotThrow(content::close, "二次 close 不再触发解码器销毁");
        } finally {
            Requests.release(req);
        }
    }
}
