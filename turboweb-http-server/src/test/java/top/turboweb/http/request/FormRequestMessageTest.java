package top.turboweb.http.request;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;
import top.turboweb.http.testkit.Requests;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormRequestMessage} 及其内容类的纯持有语义：字段与文件归档由解析器解码产出，
 * 消息只按入参原样持有；多部分内容私有持有解码器作释放锚，close 幂等。
 */
class FormRequestMessageTest {

    @Test
    void nullLineIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new FormRequestMessage(null, new FormRequestMessage.FormData(Map.of())));
        assertEquals("line must not be null", ex.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new FormRequestMessage(line, null));
        assertEquals("content must not be null", ex.getMessage());
    }

    @Test
    void formDataRejectsNullParamsMap() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new FormRequestMessage.FormData(null));
        assertEquals("formParams can not be null", ex.getMessage());
    }

    @Test
    void formDataHoldsInjectedParamsMap() {
        Map<String, List<String>> params = Map.of("name", List.of("alice"));
        FormRequestMessage.FormData content = new FormRequestMessage.FormData(params);
        assertSame(params, content.formParams(), "字段归档按入参原样持有");
    }

    @Test
    void multipartFormDataRejectsNullArguments() {
        Map<String, List<String>> params = Map.of();
        Map<String, List<MultipartFile>> files = Map.of();
        assertThrows(NullPointerException.class, () -> new FormRequestMessage.MultipartFormData(null, files, null));
        assertThrows(NullPointerException.class, () -> new FormRequestMessage.MultipartFormData(params, null, null));
        assertEquals("decoder can not be null",
                assertThrows(NullPointerException.class,
                        () -> new FormRequestMessage.MultipartFormData(params, files, null)).getMessage());
    }

    @Test
    void multipartFormDataIsFormData() {
        FormRequestMessage.MultipartFormData content =
                new FormRequestMessage.MultipartFormData(Map.of(), Map.of(), stubDecoder("a=1"));
        assertInstanceOfFormData(content);
        assertTrue(content.formFiles().isEmpty(), "未注入文件段时文件归档为空 Map");
    }

    @Test
    void multipartFormDataHoldsInjectedFilesMap() {
        Map<String, List<MultipartFile>> files = Map.of("doc", List.of());
        FormRequestMessage.MultipartFormData content =
                new FormRequestMessage.MultipartFormData(Map.of(), files, stubDecoder("a=1"));
        assertSame(files, content.formFiles(), "文件归档按入参原样持有");
        content.close();
    }

    @Test
    void closeIsIdempotent() {
        FormRequestMessage.MultipartFormData content =
                new FormRequestMessage.MultipartFormData(Map.of(), Map.of(), stubDecoder("a=1"));
        content.close();
        assertDoesNotThrow(content::close, "二次 close 不再触发解码器销毁");
    }

    @Test
    void messageCarriesInjectedLineAndTypedContent() {
        RequestLine line = new RequestLine(top.turboweb.http.core.HttpRequestMethod.POST,
                new RequestTarget("/x"));
        FormRequestMessage.FormData content = new FormRequestMessage.FormData(Map.of("a", List.of("1")));
        FormRequestMessage message = new FormRequestMessage(line, content);
        assertSame(line, message.requestLine(), "请求行按入参原样持有");
        assertSame(content, message.content(), "内容按入参原样持有且类型为 FormData");
    }

    /**
     * 建一个真实解码器充当释放锚：close 走到 HttpPostRequestDecoder.destroy 路径。
     */
    private static HttpPostRequestDecoder stubDecoder(String urlencodedBody) {
        FullHttpRequest request = Requests.postForm("/x", urlencodedBody);
        try {
            return new HttpPostRequestDecoder(
                    new DefaultHttpDataFactory(Integer.MAX_VALUE, StandardCharsets.UTF_8),
                    request, StandardCharsets.UTF_8);
        } finally {
            Requests.release(request);
        }
    }

    private static void assertInstanceOfFormData(FormRequestMessage.MultipartFormData content) {
        assertTrue(content instanceof FormRequestMessage.FormData,
                "多部分表单内容是普通表单内容的子类型");
    }
}
