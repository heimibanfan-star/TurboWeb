package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpFormContent} 的表单请求体即时解析与解码器释放。
 */
class HttpFormContentTest {

    /** multipart 夹具：一个普通字段 k 与一个文件字段 doc。 */
    private static final String MULTIPART_BODY =
            "--T\r\nContent-Disposition: form-data; name=\"k\"\r\n\r\nv\r\n"
                    + "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                    + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n";

    private static FullHttpRequest multipart() {
        return Requests.raw(HttpMethod.POST, "/up", "multipart/form-data; boundary=T",
                MULTIPART_BODY, StandardCharsets.UTF_8);
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new HttpFormContent(null, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullDefaultCharsetIsRejected() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class, () -> new HttpFormContent(req, null));
            assertEquals("defaultCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void urlencodedBodyIsParsedOnConstruction() {
        FullHttpRequest req = Requests.postForm("/x", "name=alice&age=30&tag=a&tag=b");
        try {
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
            Map<String, List<String>> params = content.formParams();
            assertEquals(List.of("alice"), params.get("name"));
            assertEquals(List.of("30"), params.get("age"));
            assertEquals(List.of("a", "b"), params.get("tag"));
            assertTrue(content.formFiles().isEmpty(), "urlencoded 无文件段，formFiles 为空 Map");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multipartBodyIsParsedOnConstruction() throws Exception {
        FullHttpRequest req = multipart();
        try {
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
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
    void nonFormContentTypeIsRejectedOnConstruction() {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest none = Requests.get("/x");
        try {
            RequestParseException ex = assertThrows(RequestParseException.class,
                    () -> new HttpFormContent(json, StandardCharsets.UTF_8));
            assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data", ex.getMessage());
            assertThrows(RequestParseException.class, () -> new HttpFormContent(none, StandardCharsets.UTF_8));
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
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
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
            HttpFormContent content = new HttpFormContent(req, Charset.forName("GBK"));
            assertEquals(List.of("张三"), content.formParams().get("n"), "头不带 charset 段时回落到构造传入的缺省字符集");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void parsedMapsAreStableAcrossCalls() {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
            assertSame(content.formParams(), content.formParams(), "解析结果一次成型，多次访问返回同一 Map");
            assertSame(content.formFiles(), content.formFiles());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void parsedDataAvailableAfterRequestRelease() throws Exception {
        // 构造期请求体字节已转入解码器持有的数据对象，请求对象可先于属性访问释放
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
        Requests.release(req);
        assertEquals(List.of("1"), content.formParams().get("a"));
        content.close();
    }

    @Test
    void malformedUrlencodedBodyPropagatesDecoderFailureOnConstruction() {
        // %zz 非合法百分号编码，Netty 构造期解码即失败；
        // 钉住口径：异常原样传播，工厂登记残留由构造器兜底清理（清理本身不可观测，异常契约可观测）
        FullHttpRequest req = Requests.raw(HttpMethod.POST, "/x",
                "application/x-www-form-urlencoded", "n=%zz", StandardCharsets.UTF_8);
        try {
            assertThrows(HttpPostRequestDecoder.ErrorDataDecoderException.class,
                    () -> new HttpFormContent(req, StandardCharsets.UTF_8));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void closeIsIdempotentAfterUrlencodedParsing() throws Exception {
        FullHttpRequest req = Requests.postForm("/x", "a=1");
        try {
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
            content.close();
            assertDoesNotThrow(content::close, "二次 close 不再触发解码器销毁");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void closeIsIdempotentAfterMultipartParsing() throws Exception {
        FullHttpRequest req = multipart();
        try {
            HttpFormContent content = new HttpFormContent(req, StandardCharsets.UTF_8);
            content.close();
            assertDoesNotThrow(content::close, "带 FileUpload 的解码器销毁后置空，二次 close 无副作用");
        } finally {
            Requests.release(req);
        }
    }
}
