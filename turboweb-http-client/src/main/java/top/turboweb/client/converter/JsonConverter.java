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
 * JSON 数据转换器实现。
 * <p>
 * 基于 {@link JsonSerializer} 提供将 HTTP 响应体 JSON 转换为 Java 对象的能力，
 * 以及将 Java 对象序列化为 JSON {@link ByteBuf} 以便发送 HTTP 请求。
 * <p>
 * 默认使用 {@link JacksonJsonSerializer} 作为 JSON 序列化工具。
 * <p>
 * 功能：
 * <ul>
 *     <li>将 {@link FullHttpResponse} 转换为指定类型对象</li>
 *     <li>将已有对象更新填充响应数据</li>
 *     <li>将对象序列化为 JSON 字节流以发送请求</li>
 * </ul>
 */
public class JsonConverter implements Converter{

    private final JsonSerializer jsonSerializer;

    /**
     * 使用自定义 JSON 序列化器构造转换器
     *
     * @param jsonSerializer 自定义 JSON 序列化器
     */
    public JsonConverter(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    /**
     * 默认构造方法，使用 {@link JacksonJsonSerializer} 作为 JSON 序列化工具
     */
    public JsonConverter() {
        this.jsonSerializer = new JacksonJsonSerializer();
    }

    /**
     * 将 HTTP 响应转换为指定类型对象。
     *
     * @param response HTTP 响应对象
     * @param type 目标类型
     * @param <T> 类型泛型
     * @return 转换后的对象
     * @throws TurboHttpClientException 如果响应为空或 Content-Type 为空
     */
    @Override
    public <T> T convert(HttpResponse response, Class<T> type) {
        String jsonString = getJsonString(response);
        if (type == String.class) {
            return type.cast(jsonString);
        }
        return jsonSerializer.jsonToBean(jsonString, type);
    }

    /**
     * 将 HTTP 响应更新到已有对象实例中。
     *
     * @param response HTTP 响应对象
     * @param object 已存在对象实例
     * @param <T> 对象类型泛型
     * @return 填充后的对象
     * @throws TurboHttpClientException 如果响应为空或 Content-Type 为空
     */
    @Override
    public <T> T convert(HttpResponse response, T object) {
        String jsonString = getJsonString(response);
        return jsonSerializer.jsonUpdateBean(jsonString, object);
    }

    /**
     * 将对象序列化为 JSON {@link ByteBuf}。
     *
     * @param object 待序列化对象
     * @param charset 编码格式
     * @return JSON 序列化后的字节流
     */
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

    /**
     * 从 HTTP 响应中获取 JSON 字符串。
     *
     * @param response HTTP 响应
     * @return 响应体的 JSON 字符串
     * @throws TurboHttpClientException 如果响应为空或 Content-Type 为空
     */
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
