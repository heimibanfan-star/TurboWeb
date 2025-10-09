package top.turboweb.client.converter;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponse;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * http客户端的请求和响应转化器
 */
public interface Converter {

    /**
     * 转换响应
     * @param response 响应
     * @param type 响应类型
     * @param <T> 类型泛型
     * @return 转换后的响应
     */
    <T> T convert(HttpResponse response, Class<T> type);

    /**
     * 响应转换
     * @param response 响应
     * @param object 响应对象
     * @param <T> 类型泛型
     * @return 响应对象
     */
    <T> T convert(HttpResponse response, T object);

    /**
     * 对象转换成字节流
     * @param object 对象
     * @param charset 编码
     * @return 字节流
     */
    ByteBuf beanConvertBuf(Object object, Charset charset);
}
