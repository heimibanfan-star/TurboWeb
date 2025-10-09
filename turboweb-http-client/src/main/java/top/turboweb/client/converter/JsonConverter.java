package top.turboweb.client.converter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.commons.exception.TurboHttpClientException;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * json转化器
 */
public class JsonConverter implements Converter{

    private final JsonSerializer jsonSerializer;

    public JsonConverter(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    public JsonConverter() {
        this.jsonSerializer = new JacksonJsonSerializer();
    }

    @Override
    public <T> T convert(HttpResponse response, Class<T> type) {
        String jsonString = getJsonString(response);
        return jsonSerializer.jsonToBean(jsonString, type);
    }

    @Override
    public <T> T convert(HttpResponse response, T object) {
        String jsonString = getJsonString(response);
        return jsonSerializer.jsonUpdateBean(jsonString, object);
    }

    @Override
    public ByteBuf beanConvertBuf(Object object, Charset charset) {
        String json;
        if (object == null) {
            json = "{}";
        } else {
            json = jsonSerializer.beanToJson(object);
        }
        return Unpooled.wrappedBuffer(json.getBytes(charset));
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
