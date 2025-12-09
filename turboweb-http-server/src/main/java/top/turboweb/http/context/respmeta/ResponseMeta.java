package top.turboweb.http.context.respmeta;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;

/**
 * {@code ResponseMeta} 表示响应元信息的配置接口。
 * <p>
 * 当控制器方法返回的对象不是 {@link io.netty.handler.codec.http.HttpResponse} 类型时，
 * TurboWeb 将根据该接口中设置的元数据对响应进行精细化控制。
 * </p>
 *
 * <h2>用途说明</h2>
 * <ul>
 *     <li>用于注解或中间件在请求处理过程中动态设置响应状态与内容类型。</li>
 *     <li>仅在框架自动构建默认响应对象时生效，用户显式返回 {@code HttpResponse} 时将被忽略。</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *     <li>仅包含与响应结构直接相关的核心属性：状态码与内容类型。</li>
 *     <li>不直接操作底层 Netty 响应对象，确保上下文解耦。</li>
 *     <li>生命周期与当前 {@code HttpContext} 一致，请求结束后自动失效。</li>
 * </ul>
 */
public interface ResponseMeta {

    /**
     * 设置响应状态码。
     *
     * @param status HTTP 状态码（例如 200、404、500）
     */
    void status(int status);

    /**
     * 设置响应状态对象。
     *
     * @param status {@link HttpResponseStatus} 对象，用于直接指定标准化状态描述
     */
    void status(HttpResponseStatus status);

    /**
     * 设置响应内容类型。
     *
     * @param contentType {@link ContentType} 对象，例如 {@code ContentType.APPLICATION_JSON}
     */
    void contentType(ContentType contentType);
}
