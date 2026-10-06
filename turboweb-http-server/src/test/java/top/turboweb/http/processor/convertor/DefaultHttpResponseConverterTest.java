package top.turboweb.http.processor.convertor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.ReactorResponse;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultHttpResponseConverter} 的类型分派与 Publisher 逐类型编码（spec §5：processor/convertor）。
 *
 * <p>全部分支都是纯内存转换：不建通道、不发网络、不起线程。断言都钉住"产品代码改了哪一行会红"：
 * 分支缺失会让响应体/Content-Type 变成另一分支的值，`processPublisher` 单个分支缺失会让对应元素编码错。
 */
class DefaultHttpResponseConverterTest {

    private static final JsonSerializer SERIALIZER = new JacksonJsonSerializer();

    private static DefaultHttpResponseConverter converter() {
        return new DefaultHttpResponseConverter(SERIALIZER);
    }

    private static String text(FullHttpResponse response) {
        return response.content().toString(GlobalConfig.getResponseCharset());
    }

    @Test
    @DisplayName("钉住 HS-23：HttpResult.createResponse 末行 set(空 headers) 在 Netty 4.2 语义下清空全部响应头")
    void httpResultDelegatesToItsOwnResponse() {
        HttpResponse response = converter().convertor(new HttpResult<>(201, "hi"));

        FullHttpResponse full = (FullHttpResponse) response;
        assertEquals(201, full.status().code());
        assertEquals("hi", text(full));
        // HttpResult.java:42 保证 headers 恒非空（两参构造传入 null 时建空 DefaultHttpHeaders），
        // :62 又无条件 response.headers().set(headers)；Netty 4.2 的 DefaultHeaders.set(Headers)
        // 语义是 clear+add，于是 :53 写下的 Content-Type 与 setContent(:76) 写下的 Content-Length
        // 都被清空——状态码与响应体不依赖请求头所以仍在。
        // 钉住 HS-23：若将来修正（末行改为逐名增量 set 而非整体清空），本用例应改为期望正确行为
        // （Content-Type=text/html;charset=utf-8、Content-Length=2）。
        assertNull(full.headers().get(HttpHeaderNames.CONTENT_TYPE),
                "HS-23：空 headers 的整体 set 清空了 Content-Type，钉住现状");
        assertNull(full.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                "HS-23：同上，Content-Length 也被清空，钉住现状");
    }

    @Test
    @DisplayName("String 走 text/html，charset 取 GlobalConfig.getResponseCharset()")
    void stringUsesHtmlContentTypeWithGlobalResponseCharset() {
        FullHttpResponse response = (FullHttpResponse) converter().convertor("hello");

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("hello", text(response));
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("null 结果返回空 text/html 响应体，而不是 NPE")
    void nullResultBecomesEmptyHtmlBody() {
        FullHttpResponse response = (FullHttpResponse) converter().convertor(null);

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("", text(response));
        assertEquals(0, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("任意对象走 JSON 序列化并标注 application/json")
    void arbitraryObjectIsJsonEncodedWithJsonContentType() {
        Map<String, Integer> payload = Map.of("a", 1);

        FullHttpResponse response = (FullHttpResponse) converter().convertor(payload);

        assertEquals(SERIALIZER.beanToJson(payload), text(response));
        assertEquals("application/json;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("已是 HttpResponse 的结果原样返回，不重新包装")
    void existingHttpResponseIsReturnedAsIs() {
        FullHttpResponse given =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        assertSame(given, converter().convertor(given));
    }

    @Test
    @DisplayName("HttpFileResult 走 createResponse：无 contentType 时回落 application/octet-stream")
    void httpFileResultBytesBecomeDownloadResponse() {
        FullHttpResponse response =
                (FullHttpResponse) converter().convertor(HttpFileResult.bytes(new byte[]{'x'}));

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("application/octet-stream",
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("x", text(response));
        assertTrue(response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION).contains("attachment"),
                "字节下载响应必须带 attachment Content-Disposition");
    }

    @Test
    @DisplayName("Publisher 走 ReactorResponse，且流内每个受支持类型都被编码成 ByteBuf")
    void publisherBecomesReactorResponseWithPerElementEncodedFlux() {
        Flux<Object> source = Flux.just(
                "a",
                new byte[]{'b'},
                ByteBuffer.wrap(new byte[]{'c'}),
                Unpooled.wrappedBuffer(new byte[]{'d'}),
                42,
                Map.of("k", "v"));

        HttpResponse response = converter().convertor(source);
        assertTrue(response instanceof ReactorResponse, "Publisher 必须转成 ReactorResponse");

        List<String> bodies = ((ReactorResponse) response).getFlux()
                .map(buf -> {
                    ByteBuf b = buf;
                    return b.toString(GlobalConfig.getResponseCharset());
                })
                .collectList()
                .block();

        assertEquals(6, bodies.size());
        assertTrue(bodies.containsAll(List.of(
                        "a", "b", "c", "d", "42", SERIALIZER.beanToJson(Map.of("k", "v")))),
                "String/byte[]/ByteBuffer/ByteBuf/Number/其他对象 六个分支都必须各自被编码，实际=" + bodies);
    }
}
