package top.turboweb.http.orchestrator;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.StreamResponseMessage;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.serializer.exception.SerializerException;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ResultToMessageHandler} 的行为锚：任意返回值归一为 HttpResult 后按数据装配，
 * 非 HttpResult 的输入包为状态码200的 HttpResult（含 null 输入），已是 HttpResult 的
 * 状态行与头原样保留。每个数据类型只走对应装配分支：String 与 null 装配为 text/html
 * 字节消息（null 为空体）、Publisher 转 StreamResponseMessage 且流内元素逐类型编码为
 * 字节数组、其余对象经 JSON 序列化装配为 application/json 字节消息。
 * 字节消息的正文与 Content-Type 缺省声明均按传入字符集；HttpResult 调用方头先整体写入，
 * 调用方已声明 Content-Type 时保留其声明，未声明时按数据类型补缺省声明，
 * 内容长度以框架按实际响应体算出的值为准；流式消息的状态行与头取自 HttpResult，
 * 未声明 Content-Type 时补 application/octet-stream。
 * 序列化失败的行为奇偶：字节消息装配上抛、流内环节转为流错误信号。
 */
class ResultToMessageHandlerTest {

    private static final JsonSerializer SERIALIZER = new JacksonJsonSerializer();
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static ResultToMessageHandler handler() {
        return new ResultToMessageHandler(SERIALIZER);
    }

    private static String text(HttpResponseMessage<?> message) {
        return new String(((ByteResponseMessage) message).content(), CHARSET);
    }

    // ---------- HttpResult 装配 ----------

    @Test
    void httpResultBecomesByteMessageKeepingStatusAndDataBranch() {
        HttpResponseMessage<?> message = handler().convert(HttpResult.create(201, "hi"), CHARSET);

        ByteResponseMessage byteMessage = assertInstanceOf(ByteResponseMessage.class, message,
                "HttpResult 必须装配为字节响应消息");
        assertEquals(201, byteMessage.statusLine().code(), "HttpResult 的状态行原样保留");
        assertEquals("hi", text(byteMessage));
        assertEquals("text/html;charset=UTF-8", byteMessage.headers().get("Content-Type"),
                "String 数据走 text/html 分支");
        assertEquals(2, Integer.parseInt(byteMessage.headers().get("Content-Length")));
    }

    @Test
    void httpResultObjectDataIsSerializedAsJson() {
        Map<String, Integer> payload = Map.of("a", 1);

        ByteResponseMessage message = (ByteResponseMessage) handler().convert(HttpResult.ok(payload), CHARSET);

        assertEquals(SERIALIZER.beanToJson(payload), text(message), "非 String 数据经 JSON 序列化");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void httpResultCallerContentTypePreservedAndContentLengthRecalculated() {
        Headers callerHeaders = new Headers();
        callerHeaders.set("X-Trace-Id", "t-1");
        callerHeaders.set("Content-Type", "text/plain");
        callerHeaders.set("Content-Length", "999");

        ByteResponseMessage message = (ByteResponseMessage) handler().convert(
                HttpResult.create(200, callerHeaders, "hi"), CHARSET);

        assertEquals("t-1", message.headers().get("X-Trace-Id"), "调用方非实体头原样保留");
        assertEquals("text/plain", message.headers().get("Content-Type"),
                "调用方已声明内容类型时保留其声明，不补缺省声明");
        assertEquals(2, Integer.parseInt(message.headers().get("Content-Length")),
                "内容长度以框架按实际响应体算出的值为准，调用方遗留值不生效");
    }

    @Test
    void httpResultNullSerializedContentProducesEmptyBody() {
        JsonSerializer nullSerializer = new JacksonJsonSerializer() {
            @Override
            public String beanToJson(Object bean) {
                return null;
            }
        };

        ByteResponseMessage message = (ByteResponseMessage)
                new ResultToMessageHandler(nullSerializer).convert(HttpResult.ok(new Object()), CHARSET);

        assertEquals(0, message.content().length, "序列化产出 null 时按空体装配");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    // ---------- String 与 null 数据：text/html ----------

    @Test
    void stringBecomesTextHtmlByteMessageWithGivenCharset() {
        ByteResponseMessage message = (ByteResponseMessage) handler().convert("hello", CHARSET);

        assertEquals(StatusLine.OK, message.statusLine(), "非 HttpResult 的输入包为状态码200的 HttpResult");
        assertEquals("hello", text(message));
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void stringEncodedByTheGivenNonDefaultCharset() {
        Charset gbk = Charset.forName("GBK");

        ByteResponseMessage message = (ByteResponseMessage) handler().convert("你好", gbk);

        assertEquals("你好", new String(message.content(), gbk), "正文按传入字符集编码");
        assertEquals("text/html;charset=GBK", message.headers().get("Content-Type"),
                "Content-Type 的 charset 段与正文编码同源");
    }

    @Test
    void nullBecomesEmptyTextHtmlByteMessage() {
        ByteResponseMessage message = (ByteResponseMessage) handler().convert(null, CHARSET);

        assertEquals(StatusLine.OK, message.statusLine());
        assertEquals(0, message.content().length, "null 数据按空体装配");
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"));
    }

    // ---------- Publisher 数据：流式消息 ----------

    @Test
    void publisherBecomesStreamMessageWithPerElementEncodedFlux() {
        Flux<Object> source = Flux.just(
                "a",
                new byte[]{'b'},
                ByteBuffer.wrap(new byte[]{'c'}),
                Unpooled.wrappedBuffer(new byte[]{'d'}),
                42,
                Map.of("k", "v"));

        HttpResponseMessage<?> message = handler().convert(source, CHARSET);

        StreamResponseMessage streamMessage = assertInstanceOf(StreamResponseMessage.class, message,
                "Publisher 必须转成流式响应消息");
        assertEquals(StatusLine.OK, message.statusLine(), "非 HttpResult 的输入包为状态码200的 HttpResult");
        assertEquals("application/octet-stream", message.headers().get("Content-Type"),
                "流式消息未声明内容类型时补缺省声明以满足写出门禁");
        List<String> bodies = streamMessage.content()
                .map(bytes -> new String(bytes, CHARSET))
                .collectList()
                .block();

        assertEquals(List.of("a", "b", "c", "d", "42", SERIALIZER.beanToJson(Map.of("k", "v"))), bodies,
                "String/byte[]/ByteBuffer/ByteBuf/Number/其他对象 六个分支都必须按序各自编码");
    }

    @Test
    void publisherWrappedInHttpResultKeepsCallerStatusAndDeclaredContentType() {
        Headers callerHeaders = new Headers();
        callerHeaders.set("Content-Type", "text/event-stream");

        HttpResponseMessage<?> message = handler().convert(
                HttpResult.create(201, callerHeaders, Flux.just("a", "b")), CHARSET);

        StreamResponseMessage streamMessage = assertInstanceOf(StreamResponseMessage.class, message,
                "HttpResult 携带的异步流必须转成流式响应消息");
        assertEquals(201, streamMessage.statusLine().code(), "流式消息的状态行取自 HttpResult");
        assertEquals("text/event-stream", streamMessage.headers().get("Content-Type"),
                "调用方已声明内容类型时保留其声明，不补缺省声明");
        assertEquals(List.of("a", "b"), streamMessage.content()
                .map(bytes -> new String(bytes, CHARSET))
                .collectList()
                .block());
    }

    @Test
    void publisherElementFailureSurfacesAsStreamErrorNotConvertThrow() {
        Flux<Object> source = Flux.just(new Object());

        HttpResponseMessage<?> message = assertDoesNotThrow(() -> handler().convert(source, CHARSET),
                "流内元素转化失败不得在 convert 调用点抛出");

        StreamResponseMessage streamMessage = (StreamResponseMessage) message;
        assertThrows(SerializerException.class, () -> streamMessage.content().blockLast(),
                "流内元素序列化失败必须转为流错误信号");
    }

    // ---------- 任意对象：JSON ----------

    @Test
    void arbitraryObjectWrapsIntoJsonMessage() {
        Map<String, Integer> payload = Map.of("a", 1);

        ByteResponseMessage message = (ByteResponseMessage) handler().convert(payload, CHARSET);

        assertEquals(SERIALIZER.beanToJson(payload), text(message),
                "非 HttpResult 的任意对象包为 HttpResult 后经 JSON 序列化装配");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void jsonSerializationFailurePropagatesUncaught() {
        assertThrows(SerializerException.class, () -> handler().convert(new Object(), CHARSET),
                "JSON 装配的序列化失败必须原样上抛，处理器不吞异常");
    }

    // ---------- 构造与入参约束 ----------

    @Test
    void nullSerializerRejectedAtConstruction() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResultToMessageHandler(null));
        assertEquals("The json serializer must not be null", ex.getMessage());
    }

    @Test
    void nullCharsetIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> handler().convert("hello", null));
        assertEquals("The charset must not be null", ex.getMessage());
    }
}
