package top.turboweb.http.orchestrator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.core.response.HttpFileResult;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.core.response.ReactorResponse;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.serializer.exception.SerializerException;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ResultConverterManager} 的行为锚：转化链按原类型分派次序逐环节认领，
 * 每个返回值类型只被其对应环节转化（HttpResult 委托自身、String 走 text/html、
 * HttpFileResult 走文件响应、HttpResponse 原样放行、null 落空 text/html、
 * Publisher 转 ReactorResponse 且流内元素逐类型编码、其余对象兜底 application/json）。
 * 序列化失败的行为奇偶：兜底环节上抛、流内环节转为流错误信号。
 */
class ResultConverterManagerTest {

    private static final JsonSerializer SERIALIZER = new JacksonJsonSerializer();

    private static ResultConverterManager manager() {
        return new ResultConverterManager(SERIALIZER);
    }

    private static String text(FullHttpResponse response) {
        return response.content().toString(GlobalConfig.getResponseCharset());
    }

    @Test
    void httpResultKeepsItsOwnStatusAndDelegatesToCreateResponse() {
        // 状态 201 甄别 HttpResult 环节命中：String 环节产出的响应恒为 200
        HttpResponse response = manager().convert(new HttpResult<>(201, "hi"));

        FullHttpResponse full = (FullHttpResponse) response;
        assertEquals(201, full.status().code(), "HttpResult 必须委托自身 createResponse 保留状态码");
        assertEquals("hi", text(full));
        assertEquals("text/html;charset=utf-8", full.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(2, full.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
    }

    @Test
    void stringBecomesTextHtmlResponseWithGlobalResponseCharset() {
        FullHttpResponse response = (FullHttpResponse) manager().convert("hello");

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("hello", text(response));
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(5, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
    }

    @Test
    void fileResultBytesBecomeOctetStreamAttachmentResponse() {
        FullHttpResponse response =
                (FullHttpResponse) manager().convert(HttpFileResult.bytes(new byte[]{'x'}));

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("application/octet-stream", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("x", text(response));
        assertTrue(response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION).contains("attachment"),
                "字节下载响应必须带 attachment Content-Disposition");
    }

    @Test
    void existingHttpResponsePassesThroughUnchanged() {
        FullHttpResponse given =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        assertSame(given, manager().convert(given), "已是 HttpResponse 的结果原样返回，不重新包装");
    }

    @Test
    void nullBecomesEmptyTextHtmlResponse() {
        FullHttpResponse response = (FullHttpResponse) manager().convert(null);

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("", text(response));
        assertEquals(0, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    void publisherBecomesReactorResponseWithPerElementEncodedFlux() {
        Flux<Object> source = Flux.just(
                "a",
                new byte[]{'b'},
                ByteBuffer.wrap(new byte[]{'c'}),
                Unpooled.wrappedBuffer(new byte[]{'d'}),
                42,
                Map.of("k", "v"));

        HttpResponse response = manager().convert(source);
        ReactorResponse reactorResponse = assertInstanceOf(ReactorResponse.class, response,
                "Publisher 必须转成 ReactorResponse");

        List<String> bodies = reactorResponse.getFlux()
                .map(buf -> ((ByteBuf) buf).toString(GlobalConfig.getResponseCharset()))
                .collectList()
                .block();

        assertEquals(List.of("a", "b", "c", "d", "42", SERIALIZER.beanToJson(Map.of("k", "v"))), bodies,
                "String/byte[]/ByteBuffer/ByteBuf/Number/其他对象 六个分支都必须按序各自编码");
    }

    @Test
    void arbitraryObjectFallsThroughToJsonResponse() {
        Map<String, Integer> payload = Map.of("a", 1);

        FullHttpResponse response = (FullHttpResponse) manager().convert(payload);

        assertEquals(SERIALIZER.beanToJson(payload), text(response));
        assertEquals("application/json;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    void jsonFallbackSerializationFailurePropagatesUncaught() {
        assertThrows(SerializerException.class, () -> manager().convert(new Object()),
                "兜底环节序列化失败必须原样上抛，管理器不吞异常");
    }

    @Test
    void publisherElementFailureSurfacesAsStreamErrorNotConvertThrow() {
        Flux<Object> source = Flux.just(new Object());

        HttpResponse response = assertDoesNotThrow(() -> manager().convert(source),
                "流内元素转化失败不得在 convert 调用点抛出");

        ReactorResponse reactorResponse = (ReactorResponse) response;
        assertThrows(SerializerException.class, () -> reactorResponse.getFlux().blockLast(),
                "流内元素序列化失败必须转为流错误信号");
    }
}
