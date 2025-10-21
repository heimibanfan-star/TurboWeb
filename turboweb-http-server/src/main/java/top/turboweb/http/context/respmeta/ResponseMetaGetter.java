package top.turboweb.http.context.respmeta;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;

/**
 * {@code ResponseMetaGetter} 用于提供响应元信息的只读访问能力。
 * <p>
 * 该接口通常由框架内部实现，用于在响应构建阶段读取由
 * {@link ResponseMeta} 设置的状态码与内容类型，从而在生成默认响应时
 * 应用对应配置。
 * </p>
 *
 * <p><strong>设计说明：</strong></p>
 * <ul>
 *     <li>仅暴露响应状态与内容类型的读取能力，不包含写入方法。</li>
 *     <li>常用于 {@code HttpContext} 内部或响应构建器中读取响应配置。</li>
 *     <li>与 {@code ResponseMeta} 的生命周期一致，随请求上下文存在。</li>
 * </ul>
 */
public interface ResponseMetaGetter {

    /**
     * 获取当前响应状态对象。
     *
     * @return {@link HttpResponseStatus} 实例，若未显式设置则返回框架默认值（通常为 200 OK）
     */
    HttpResponseStatus getStatus();

    /**
     * 获取当前响应内容类型。
     *
     * @return {@link ContentType} 实例，若未显式设置则返回框架默认值（通常为 {@code text/plain; charset=UTF-8}）
     */
    ContentType getContentType();
}
