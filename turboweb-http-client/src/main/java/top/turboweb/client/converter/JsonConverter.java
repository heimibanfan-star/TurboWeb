package top.turboweb.client.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.commons.exception.TurboHttpClientException;
import top.turboweb.commons.exception.TurboSerializableException;
import top.turboweb.commons.utils.base.BeanUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * json转化器
 */
public class JsonConverter implements Converter{
    @Override
    public <T> T convert(HttpResponse response, Class<T> type) {
        String jsonString = getJsonString(response);
        try {
            return BeanUtils.getObjectMapper().readValue(jsonString, type);
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }

    @Override
    public <T> T convert(HttpResponse response, T object) {
        String jsonString = getJsonString(response);
        try {
            return BeanUtils.getObjectMapper().readerForUpdating(object).readValue(jsonString);
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }

    private String getJsonString(HttpResponse response) {
        // 判断是否有请求体
        if (response instanceof FullHttpResponse fullHttpResponse) {
            String contentTypeStr = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
            if (contentTypeStr == null) {
                throw new TurboHttpClientException("Response Content-Type is null");
            }
            ContentType contentType = ContentType.parse(contentTypeStr);
            // 获取响应编码
            Charset charset = contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
            // 获取请求体
            ByteBuf byteBuf = fullHttpResponse.content();
            return byteBuf.toString(charset);
        } else {
            throw new TurboHttpClientException("empty response");
        }
    }
}
