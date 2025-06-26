package top.turboweb.http.processor.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.exception.TurboSerializableException;
import top.turboweb.commons.utils.base.BeanUtils;

import java.nio.charset.StandardCharsets;

/**
 * 默认的HttpResponse转化器
 */
public class DefaultHttpResponseConverter implements HttpResponseConverter {

    @Override
    public HttpResponse convertor(Object result) {
        return switch (result) {
            // 如果无返回值，则返回空字符串
            case null -> buildResponse("", "text/plain;charset=" + StandardCharsets.UTF_8);
            // 如果是正常的反应类型，则直接返回
            case HttpResponse httpResponse -> httpResponse;
            // 如果是字符串，按照text/html构建
            case String string -> buildResponse(string, "text/html;charset=" + StandardCharsets.UTF_8);
            // 按照application/json构建
            default -> {
                try {
                    String json = BeanUtils.getObjectMapper().writeValueAsString(result);
                    yield buildResponse(json, "application/json;charset=" + StandardCharsets.UTF_8);
                } catch (JsonProcessingException e) {
                    throw new TurboSerializableException(e);
                }
            }
        };
    }

    /**
     * 构建一个HttpResponse
     *
     * @param content       内容
     * @param contentType   内容类型
     * @return HttpResponse 响应对象
     */
    private HttpResponse buildResponse(String content, String contentType) {
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.content().writeBytes(content.getBytes(StandardCharsets.UTF_8));
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
        return fullHttpResponse;
    }
}
