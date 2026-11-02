package top.turboweb.http.orchestrator.convert;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.response.StreamResponseMessage;
import top.turboweb.serializer.JsonSerializer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

/** 内置环节：认领 Publisher，转为流式响应消息。 */
public final class PublisherConvertHandler implements ConvertHandler {

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";

    private final JsonSerializer jsonSerializer;

    /**
     * 构造 Publisher 转化处理器。
     *
     * @param jsonSerializer json序列化器，流内其他类型元素经其编码，不能为null
     */
    public PublisherConvertHandler(JsonSerializer jsonSerializer) {
        this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
    }

    @Override
    public ConvertHandler.Result convert(Object result, Charset charset) {
        return result instanceof Publisher<?> publisher
                ? ConvertHandler.Result.finished(toStreamMessage(publisher, charset))
                : ConvertHandler.Result.shouldContinue();
    }

    /**
     * 将异步流包装为流式响应消息：消息携带 application/octet-stream 内容类型
     * 以满足写出门禁，流内元素按 {@link #processPublisher(Publisher, Charset)} 逐个编码为字节块。
     *
     * @param publisher 异步流
     * @param charset   装配期判定的缺省字符集
     * @return 流式响应消息
     */
    private StreamResponseMessage toStreamMessage(Publisher<?> publisher, Charset charset) {
        StreamResponseMessage message = new StreamResponseMessage(StatusLine.OK, processPublisher(publisher, charset));
        message.headers().set(CONTENT_TYPE, "application/octet-stream");
        return message;
    }

    /**
     * 将异步流逐元素编码为字节数组：String 与 Number 按给定字符集取字节，byte[] 原样透传，
     * ByteBuffer 与 ByteBuf 复制为字节数组，其余对象经 json序列化器编码为json后按该字符集取字节；
     * 元素序列化失败转为流错误信号，不在订阅前的调用点抛出。
     *
     * @param publisher 异步流
     * @param charset   装配期判定的缺省字符集
     * @return 流式响应消息的响应体流
     */
    private Flux<byte[]> processPublisher(Publisher<?> publisher, Charset charset) {
        return Flux.from(publisher)
                .flatMap(val -> {
                    // 处理类型转化
                    if (val instanceof String s) {
                        return Mono.just(s.getBytes(charset));
                    } else if (val instanceof byte[] bytes) {
                        return Mono.just(bytes);
                    } else if (val instanceof ByteBuffer buffer) {
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.duplicate().get(bytes);
                        return Mono.just(bytes);
                    } else if (val instanceof ByteBuf byteBuf) {
                        return Mono.just(ByteBufUtil.getBytes(byteBuf));
                    } else if (val instanceof Number number) {
                        return Mono.just(number.toString().getBytes(charset));
                    } else {
                        try {
                            String json = jsonSerializer.beanToJson(val);
                            return Mono.just(json.getBytes(charset));
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }
                });
    }
}
