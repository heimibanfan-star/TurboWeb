package top.turboweb.http.orchestrator;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.StreamResponseMessage;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 结果转化处理器：把业务返回值装配为待写出的响应消息，恒产出一条响应消息，永不为null。
 * <p>
 * 非 {@link HttpResult} 的返回值一律包为状态码200的 HttpResult 后按数据装配，
 * 业务侧需要自定义状态行与响应头时直接返回 HttpResult；
 * 已是响应消息的返回值由编排器直接透传写出，不经本类。
 * 数据按类型装配：Publisher 转为流式响应消息，状态行与响应头取自 HttpResult，
 * 调用方已声明 Content-Type 时保留其声明，未声明时补 text/html 且 charset 段取缺省字符集；
 * String 与 null 数据按 text/html 装配为字节消息，null 为空体；
 * byte[] 数据原样作为响应体，缺省内容类型为 application/octet-stream；
 * 其余数据经 json序列化器按 application/json 装配为字节消息。
 * 调用方头先整体写入，调用方已声明 Content-Type 的字节消息保留其声明不覆盖，
 * 仅在缺失时按数据类型补缺省声明，内容长度恒以框架按实际响应体算出的值为准。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object, Charset)} 携带装配期判定的缺省字符集
 * （响应编码不受请求头声明影响，恒取缺省配置），入参 result 允许为 null：
 * 正文编码与缺省 Content-Type 的 charset 段一律取该字符集。
 * 序列化失败按位置各自传导：字节消息在调用点原样上抛，
 * 流内元素转为流错误信号，本类不吞异常。
 * </p>
 */
public final class ResultToMessageHandler {

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";
    /** Content-Length 头名 */
    private static final String CONTENT_LENGTH = "Content-Length";

    /**
     * json序列化器：非字符串数据的装配与流内其他类型元素编码经其序列化。
     */
    private final JsonSerializer jsonSerializer;

    /**
     * 构造结果转化处理器。
     *
     * @param jsonSerializer json序列化器，非字符串数据与流内其他类型元素编码经其序列化，不能为null
     * @throws NullPointerException json序列化器为null
     */
    public ResultToMessageHandler(JsonSerializer jsonSerializer) {
        this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
    }

    /**
     * 把业务返回值装配为响应消息：非 HttpResult 的输入包为状态码200的 HttpResult 后装配。
     *
     * @param result  业务返回值，可能为null
     * @param charset 装配期判定的缺省字符集，用于正文编码与缺省内容类型声明
     * @return 转化出的响应消息，永不为null
     * @throws NullPointerException charset 为 null
     */
    public HttpResponseMessage<?> convert(Object result, Charset charset) {
        Objects.requireNonNull(charset, "The charset must not be null");
        return result instanceof HttpResult httpResult
                ? toMessage(httpResult, charset)
                : toMessage(HttpResult.ok(result), charset);
    }

    /**
     * 把 HttpResult 值对象装配为响应消息：数据为 Publisher 时转流式响应消息，
     * String 与 null 数据按 text/html 装配为字节消息（null 为空体），
     * byte[] 数据原样作为响应体装配为字节消息，
     * 其余数据经 json序列化器按 application/json 装配为字节消息。
     *
     * @param httpResult 响应值对象
     * @param charset    装配期判定的缺省字符集
     * @return 转化出的响应消息
     */
    private HttpResponseMessage<?> toMessage(HttpResult httpResult, Charset charset) {
        Object data = httpResult.data();
        if (data instanceof Publisher<?> publisher) {
            return toStreamMessage(httpResult, publisher, charset);
        }
        byte[] body;
        String defaultContentType;
        if (data instanceof String stringData) {
            body = stringData.getBytes(charset);
            defaultContentType = "text/html;charset=" + charset.name();
        } else if (data instanceof byte[] byteData) {
            body = byteData;
            defaultContentType = "application/octet-stream";
        } else if (data == null) {
            body = new byte[0];
            defaultContentType = "text/html;charset=" + charset.name();
        } else {
            String content = jsonSerializer.beanToJson(data);
            body = content == null ? new byte[0] : content.getBytes(charset);
            defaultContentType = "application/json;charset=" + charset.name();
        }
        ByteResponseMessage message = new ByteResponseMessage(httpResult.statusLine(), body);
        copyCallerHeaders(httpResult, message);
        // 调用方已声明内容类型时保留其声明，仅在缺失时按数据类型补缺省声明
        if (!message.headers().contains(CONTENT_TYPE)) {
            message.headers().set(CONTENT_TYPE, defaultContentType);
        }
        message.headers().set(CONTENT_LENGTH, String.valueOf(body.length));
        return message;
    }

    /**
     * 将 HttpResult 携带的异步流包装为流式响应消息：状态行与响应头取自 HttpResult，
     * 调用方已声明 Content-Type 时保留其声明，未声明时补 text/html 且 charset 段取
     * 缺省字符集以满足写出门禁；流内元素按 {@link #processPublisher(Publisher, Charset)} 逐个编码为字节块。
     *
     * @param httpResult 响应值对象
     * @param publisher  异步流
     * @param charset    装配期判定的缺省字符集
     * @return 流式响应消息
     */
    private StreamResponseMessage toStreamMessage(HttpResult httpResult, Publisher<?> publisher, Charset charset) {
        StreamResponseMessage message = new StreamResponseMessage(
                httpResult.statusLine(), processPublisher(publisher, charset));
        copyCallerHeaders(httpResult, message);
        if (!message.headers().contains(CONTENT_TYPE)) {
            message.headers().set(CONTENT_TYPE, "text/html;charset=" + charset.name());
        }
        return message;
    }

    /**
     * 把 HttpResult 的调用方头整体写入消息。
     *
     * @param httpResult 响应值对象
     * @param message    装配中的响应消息
     */
    private static void copyCallerHeaders(HttpResult httpResult, HttpResponseMessage<?> message) {
        for (Map.Entry<String, List<String>> entry : httpResult.headers().asMap().entrySet()) {
            for (String value : entry.getValue()) {
                message.headers().add(entry.getKey(), value);
            }
        }
    }

    /**
     * 将异步流逐元素编码为字节数组：String 按给定字符集取字节，byte[] 原样透传，
     * 其余对象经 json序列化器编码为json后按该字符集取字节；
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
