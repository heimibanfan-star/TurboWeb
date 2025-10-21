package top.turboweb.client.converter;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponse;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * HTTP 客户端请求与响应数据转换器接口。
 * <p>
 * 提供：
 * <ul>
 *     <li>将 {@link HttpResponse} 转换为指定类型的对象</li>
 *     <li>将任意对象转换为 {@link ByteBuf} 以便发送 HTTP 请求</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *     <li>在 {@link top.turboweb.client.DefaultTurboHttpClient} 中自动序列化请求体和反序列化响应体</li>
 *     <li>支持自定义数据格式，例如 JSON、XML 或二进制</li>
 * </ul>
 */
public interface Converter {

    /**
     * 将 HTTP 响应转换为指定类型。
     *
     * @param response HTTP 响应对象
     * @param type 响应目标类型
     * @param <T> 响应类型泛型
     * @return 转换后的对象实例
     */
    <T> T convert(HttpResponse response, Class<T> type);


    /**
     * 将 HTTP 响应转换并填充到指定对象实例中。
     * <p>
     * 适用于响应已经存在对象实例的场景。
     *
     * @param response HTTP 响应对象
     * @param object 已存在对象实例
     * @param <T> 对象类型泛型
     * @return 填充后的对象实例
     */
    <T> T convert(HttpResponse response, T object);

    /**
     * 将对象序列化为 {@link ByteBuf} 以便发送 HTTP 请求。
     * <p>
     * 支持序列化成 JSON、表单或自定义格式，依赖具体实现。
     *
     * @param object 待序列化对象
     * @param charset 编码格式
     * @return 序列化后的 {@link ByteBuf} 对象
     */
    ByteBuf beanConvertBuf(Object object, Charset charset);
}
