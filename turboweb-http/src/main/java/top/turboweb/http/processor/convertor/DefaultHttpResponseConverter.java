package top.turboweb.http.processor.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboSerializableException;
import top.turboweb.commons.utils.base.BeanUtils;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;

import java.nio.charset.StandardCharsets;

/**
 * 默认的HttpResponse转化器
 */
public class DefaultHttpResponseConverter implements HttpResponseConverter {

    @Override
    public HttpResponse convertor(Object result) {
        return switch (result) {
            // 处理HttpResult的类型
            case HttpResult<?> httpResult -> httpResult.createResponse();
            // 如果是字符串，按照text/html构建
            case String string -> buildResponse(string, "text/html;charset=" + GlobalConfig.getResponseCharset().name());
            // 处理HttpFileResult的类型
            case HttpFileResult httpFileResult -> httpFileResult.createResponse();
            // 如果是正常的反应类型，则直接返回
            case HttpResponse httpResponse -> httpResponse;
            // 如果无返回值，则返回空字符串
            case null -> buildResponse("", "text/plain;charset=" + GlobalConfig.getResponseCharset().name());
            // 按照application/json构建
            default -> {
                try {
                    String json = BeanUtils.getObjectMapper().writeValueAsString(result);
                    yield buildResponse(json, "application/json;charset=" + GlobalConfig.getResponseCharset().name());
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
        fullHttpResponse.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
        return fullHttpResponse;
    }
}
