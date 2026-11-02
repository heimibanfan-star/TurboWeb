package top.turboweb.http.orchestrator.convert;

import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.response.HttpResult;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 内置环节：认领 HttpResult，装配为字节响应消息。 */
public final class HttpResultConvertHandler implements ConvertHandler {

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";
    /** Content-Length 头名 */
    private static final String CONTENT_LENGTH = "Content-Length";

    private final JsonSerializer jsonSerializer;

    /**
     * 构造 HttpResult 转化处理器。
     *
     * @param jsonSerializer json序列化器，非字符串数据经其序列化为 JSON，不能为null
     */
    public HttpResultConvertHandler(JsonSerializer jsonSerializer) {
        this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
    }

    @Override
    public ConvertHandler.Result convert(Object result, Charset charset) {
        return result instanceof HttpResult httpResult
                ? ConvertHandler.Result.finished(toByteMessage(httpResult, charset))
                : ConvertHandler.Result.shouldContinue();
    }

    /**
     * 把 HttpResult 值对象装配为字节响应消息：数据为 String 时按 text/html 输出，
     * 其余数据经 json 序列化器按 application/json 输出，正文与缺省 Content-Type 的 charset 段
     * 均取给定字符集；调用方头先整体写入，调用方已声明 Content-Type 时保留其声明不覆盖，
     * 仅在缺失时按数据类型补缺省声明，内容长度恒以框架按实际响应体算出的值为准。
     *
     * @param httpResult 响应值对象
     * @param charset    装配期判定的缺省字符集
     * @return 字节响应消息
     */
    private ByteResponseMessage toByteMessage(HttpResult httpResult, Charset charset) {
        String content;
        String defaultContentType;
        if (httpResult.data() instanceof String stringData) {
            content = stringData;
            defaultContentType = "text/html;charset=" + charset.name();
        } else {
            content = jsonSerializer.beanToJson(httpResult.data());
            defaultContentType = "application/json;charset=" + charset.name();
        }
        byte[] body = content == null ? new byte[0] : content.getBytes(charset);
        ByteResponseMessage message = new ByteResponseMessage(httpResult.statusLine(), body);
        for (Map.Entry<String, List<String>> entry : httpResult.headers().asMap().entrySet()) {
            for (String value : entry.getValue()) {
                message.headers().add(entry.getKey(), value);
            }
        }
        // 调用方已声明内容类型时保留其声明，仅在缺失时按数据类型补缺省声明
        if (!message.headers().contains(CONTENT_TYPE)) {
            message.headers().set(CONTENT_TYPE, defaultContentType);
        }
        message.headers().set(CONTENT_LENGTH, String.valueOf(body.length));
        return message;
    }
}
