package top.turboweb.http.orchestrator.convert;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConvertHandlerManager} 的行为锚：转化链按原类型分派次序逐环节认领，
 * 每个返回值类型只被其对应环节转化为响应消息（HttpResult 装配为字节消息、String 走
 * text/html、null 落空 text/html、Publisher 转 StreamResponseMessage 且流内元素逐类型
 * 编码为字节数组、其余对象兜底 application/json）。各环节自建消息的正文与 Content-Type
 * 均按传入字符集；HttpResult 调用方头先整体写入、实体头以框架按实际响应体算出的值为准。
 * 序列化失败的行为奇偶：兜底环节上抛、流内环节转为流错误信号。
 * 自定义环节契约：构造期以处理器具体类登记，内置类型（含 JSON 默认环节）拒登、
 * 同类重复拒登、列表与元素为 null 拒登；分派时内置在前、自定义按登记次序在后，
 * 两级都不认领时由 JSON 默认环节认领。
 */
class ConvertHandlerManagerTest {

    private static final JsonSerializer SERIALIZER = new JacksonJsonSerializer();
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static ConvertHandlerManager manager() {
        return new ConvertHandlerManager(SERIALIZER, List.of());
    }

    private static String text(HttpResponseMessage<?> message) {
        return new String(((ByteResponseMessage) message).content(), CHARSET);
    }

    // ---------- HttpResult 环节 ----------

    @Test
    void httpResultBecomesByteMessageKeepingStatusAndDataBranch() {
        HttpResponseMessage<?> message = manager().convert(HttpResult.create(201, "hi"), CHARSET);

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

        ByteResponseMessage message = (ByteResponseMessage) manager().convert(HttpResult.ok(payload), CHARSET);

        assertEquals(SERIALIZER.beanToJson(payload), text(message), "非 String 数据经 JSON 序列化");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void httpResultCallerHeadersCarriedButEntityHeadersRecalculated() {
        Headers callerHeaders = new Headers();
        callerHeaders.set("X-Trace-Id", "t-1");
        callerHeaders.set("Content-Type", "text/plain");
        callerHeaders.set("Content-Length", "999");

        ByteResponseMessage message = (ByteResponseMessage) manager().convert(
                HttpResult.create(200, callerHeaders, "hi"), CHARSET);

        assertEquals("t-1", message.headers().get("X-Trace-Id"), "调用方非实体头原样保留");
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"),
                "内容类型以框架按实际内容算出的值为准");
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
                new ConvertHandlerManager(nullSerializer, List.of()).convert(HttpResult.ok(new Object()), CHARSET);

        assertEquals(0, message.content().length, "序列化产出 null 时按空体装配");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    // ---------- String 与 null 环节 ----------

    @Test
    void stringBecomesTextHtmlByteMessageWithGivenCharset() {
        ByteResponseMessage message = (ByteResponseMessage) manager().convert("hello", CHARSET);

        assertEquals(StatusLine.OK, message.statusLine());
        assertEquals("hello", text(message));
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void stringEncodedByTheGivenNonDefaultCharset() {
        Charset gbk = Charset.forName("GBK");

        ByteResponseMessage message = (ByteResponseMessage) manager().convert("你好", gbk);

        assertEquals("你好", new String(message.content(), gbk), "正文按传入字符集编码");
        assertEquals("text/html;charset=GBK", message.headers().get("Content-Type"),
                "Content-Type 的 charset 段与正文编码同源");
    }

    @Test
    void nullBecomesEmptyTextHtmlByteMessage() {
        ByteResponseMessage message = (ByteResponseMessage) manager().convert(null, CHARSET);

        assertEquals(StatusLine.OK, message.statusLine());
        assertEquals(0, message.content().length);
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"));
    }

    // ---------- Publisher 环节 ----------

    @Test
    void publisherBecomesStreamMessageWithPerElementEncodedFlux() {
        Flux<Object> source = Flux.just(
                "a",
                new byte[]{'b'},
                ByteBuffer.wrap(new byte[]{'c'}),
                Unpooled.wrappedBuffer(new byte[]{'d'}),
                42,
                Map.of("k", "v"));

        HttpResponseMessage<?> message = manager().convert(source, CHARSET);

        StreamResponseMessage streamMessage = assertInstanceOf(StreamResponseMessage.class, message,
                "Publisher 必须转成流式响应消息");
        assertEquals("application/octet-stream", message.headers().get("Content-Type"),
                "流式消息须携带内容类型以满足写出门禁");
        List<String> bodies = streamMessage.content()
                .map(bytes -> new String(bytes, CHARSET))
                .collectList()
                .block();

        assertEquals(List.of("a", "b", "c", "d", "42", SERIALIZER.beanToJson(Map.of("k", "v"))), bodies,
                "String/byte[]/ByteBuffer/ByteBuf/Number/其他对象 六个分支都必须按序各自编码");
    }

    @Test
    void publisherElementFailureSurfacesAsStreamErrorNotConvertThrow() {
        Flux<Object> source = Flux.just(new Object());

        HttpResponseMessage<?> message = assertDoesNotThrow(() -> manager().convert(source, CHARSET),
                "流内元素转化失败不得在 convert 调用点抛出");

        StreamResponseMessage streamMessage = (StreamResponseMessage) message;
        assertThrows(SerializerException.class, () -> streamMessage.content().blockLast(),
                "流内元素序列化失败必须转为流错误信号");
    }

    // ---------- JSON 默认环节 ----------

    @Test
    void arbitraryObjectFallsThroughToJsonMessage() {
        Map<String, Integer> payload = Map.of("a", 1);

        ByteResponseMessage message = (ByteResponseMessage) manager().convert(payload, CHARSET);

        assertEquals(SERIALIZER.beanToJson(payload), text(message));
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void jsonFallbackSerializationFailurePropagatesUncaught() {
        assertThrows(SerializerException.class, () -> manager().convert(new Object(), CHARSET),
                "兜底环节序列化失败必须原样上抛，管理器不吞异常");
    }

    @Test
    void nullCharsetIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> manager().convert("hello", null));
        assertEquals("The charset must not be null", ex.getMessage());
    }

    // ---------- 自定义处理器注册契约 ----------

    @Test
    void nullSerializerAndNullCustomListRejectedAtConstruction() {
        NullPointerException serializerEx = assertThrows(NullPointerException.class,
                () -> new ConvertHandlerManager(null, List.of()));
        assertEquals("The json serializer must not be null", serializerEx.getMessage());

        NullPointerException listEx = assertThrows(NullPointerException.class,
                () -> new ConvertHandlerManager(SERIALIZER, null));
        assertEquals("The custom convert handler list must not be null", listEx.getMessage());
    }

    @Test
    void nullCustomElementRejectedAtConstruction() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ConvertHandlerManager(SERIALIZER, Arrays.asList((ConvertHandler) null)));
        assertEquals("The convert handler must not be null", ex.getMessage());
    }

    @Test
    void builtinConvertHandlerClassRejectedFromCustomRegistration() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ConvertHandlerManager(SERIALIZER,
                        List.of(new ConvertHandlerManager.StringConvertHandler())));
        assertEquals("Can not register a built-in convert handler: "
                + ConvertHandlerManager.StringConvertHandler.class.getName(), ex.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> new ConvertHandlerManager(SERIALIZER,
                        List.of(new ConvertHandlerManager.JsonResultConvertHandler(SERIALIZER))),
                "JSON 默认环节同为内置处理器，注册照样拒登");
    }

    @Test
    void duplicateCustomConvertHandlerClassRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ConvertHandlerManager(SERIALIZER,
                        List.of(new FirstMarkerConvertHandler(), new FirstMarkerConvertHandler())));
        assertEquals("Duplicate convert handler registration: " + FirstMarkerConvertHandler.class.getName(),
                ex.getMessage());
    }

    // ---------- 内置、自定义、JSON 默认三级分派 ----------

    @Test
    void builtinConvertHandlerTakesPrecedenceOverCustom() {
        ConvertHandlerManager manager = new ConvertHandlerManager(
                SERIALIZER, List.of(new FirstMarkerConvertHandler()));

        HttpResponseMessage<?> message = manager.convert("hi", CHARSET);

        assertEquals("hi", text(message), "String 已被内置环节认领，自定义环节拿不到");
        assertEquals("text/html;charset=UTF-8", message.headers().get("Content-Type"));
    }

    @Test
    void customConvertHandlerIsConsultedBeforeJsonDefault() {
        ConvertHandlerManager manager = new ConvertHandlerManager(
                SERIALIZER, List.of(new FirstMarkerConvertHandler()));

        HttpResponseMessage<?> message = manager.convert(42, CHARSET);

        assertEquals("first", text(message),
                "内置环节都不认领 Integer，自定义环节先于 JSON 默认环节认领");
    }

    @Test
    void customConvertHandlersAreConsultedInRegistrationOrder() {
        ConvertHandlerManager manager = new ConvertHandlerManager(
                SERIALIZER, List.of(new FirstMarkerConvertHandler(), new SecondMarkerConvertHandler()));

        HttpResponseMessage<?> message = manager.convert(42, CHARSET);

        assertEquals("first", text(message), "多个自定义环节都认领时，登记在前者胜出");
    }

    @Test
    void decliningCustomConvertHandlerFallsThroughToJsonDefault() {
        ConvertHandlerManager manager = new ConvertHandlerManager(
                SERIALIZER, List.of(new DeclineConvertHandler()));
        Map<String, Integer> payload = Map.of("a", 1);

        HttpResponseMessage<?> message = manager.convert(payload, CHARSET);

        assertEquals(SERIALIZER.beanToJson(payload), text(message),
                "自定义环节不认领时落到 JSON 默认处理器");
        assertEquals("application/json;charset=UTF-8", message.headers().get("Content-Type"));
    }

    // ---------- 夹具 ----------

    /** 自定义环节夹具：认领任意返回值，产出正文为 first 的字节消息。 */
    private static final class FirstMarkerConvertHandler implements ConvertHandler {

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return ConvertHandler.Result.finished(new ByteResponseMessage(
                    StatusLine.OK, "first".getBytes(charset)));
        }
    }

    /** 自定义环节夹具：认领任意返回值，产出正文为 second 的字节消息（与前一枚并存以测试登记次序）。 */
    private static final class SecondMarkerConvertHandler implements ConvertHandler {

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return ConvertHandler.Result.finished(new ByteResponseMessage(
                    StatusLine.OK, "second".getBytes(charset)));
        }
    }

    /** 自定义环节夹具：恒不认领，全部放行。 */
    private static final class DeclineConvertHandler implements ConvertHandler {

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return ConvertHandler.Result.shouldContinue();
        }
    }
}
