package top.turboweb.http.context.respmeta;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;

/**
 * {@code DefaultResponseMeta} 是 {@link ResponseMetaGetter} 和 {@link ResponseMeta} 的默认实现，
 * 用于在返回非 {@code HttpResponse} 类型结果时，提供响应状态码与内容类型的配置支持。
 * <p>
 * 该类通常由框架在响应阶段自动创建，或由自定义注解逻辑设置，
 * 用于为 TurboWeb 构建默认响应对象时提供必要的元信息。
 * </p>
 *
 * <p><strong>设计说明：</strong></p>
 * <ul>
 *     <li>封装响应的基本元信息，包括状态码与内容类型。</li>
 *     <li>由 {@code HttpContext} 或中间件持有，并在响应生成阶段读取。</li>
 *     <li>非线程安全，每个请求上下文应使用独立实例。</li>
 * </ul>
 *
 * <p><strong>示例：</strong></p>
 * <pre>{@code
 * DefaultResponseMeta meta = new DefaultResponseMeta();
 * meta.status(HttpResponseStatus.OK);
 * meta.contentType(ContentType.APPLICATION_JSON);
 * }</pre>
 */
public class DefaultResponseMeta implements ResponseMetaGetter, ResponseMeta {

    private HttpResponseStatus status;
    private ContentType contentType;

    @Override
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public ContentType getContentType() {
        return contentType;
    }

    @Override
    public void status(int status) {
        this.status = HttpResponseStatus.valueOf(status);
    }

    @Override
    public void status(HttpResponseStatus status) {
        this.status = status;
    }

    @Override
    public void contentType(ContentType contentType) {
        this.contentType = contentType;
    }
}
