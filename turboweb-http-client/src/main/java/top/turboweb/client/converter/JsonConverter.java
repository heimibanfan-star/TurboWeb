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
            String json = byteBuf.toString(charset);
            // 进行序列化
            try {
                return BeanUtils.getObjectMapper().readValue(json, type);
            } catch (JsonProcessingException e) {
                throw new TurboSerializableException(e);
            }
        } else {
            throw new TurboHttpClientException("empty response");
        }

    }
}
