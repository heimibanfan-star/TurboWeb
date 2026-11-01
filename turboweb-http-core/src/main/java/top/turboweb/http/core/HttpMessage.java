package top.turboweb.http.core;

/**
 * http消息的读写契约：平铺承载消息行与消息体内容的读取，消息头以独立的
 * {@link Headers} 实体承载，经 {@link #headers()} 获取后读写；
 * 消息行与消息体内容的类型均由子契约以泛型实参填充（如 http 请求消息以 {@link RequestLine} 填充请求行），
 * 无消息体的消息以 {@link Void} 填充内容类型，{@link #content()} 恒返回 {@code null}。
 *
 * @param <LINE>    消息行信息的类型
 * @param <CONTENT> 消息体内容的类型
 */
public interface HttpMessage<LINE, CONTENT> {

    /**
     * 获取消息行信息：请求消息为请求行，响应消息为状态行。
     *
     * @return 消息行信息
     */
    LINE line();

    /**
     * 获取消息体内容：无消息体的消息（内容类型为 {@link Void}）恒返回 {@code null}。
     *
     * @return 消息体内容
     */
    CONTENT content();

    /**
     * 获取消息自有的消息头实体：读写直接作用于消息，大小写不敏感等头语义见 {@link Headers}。
     *
     * @return 消息头实体
     */
    Headers headers();
}
