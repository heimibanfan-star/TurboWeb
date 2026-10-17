package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.rest.Http;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * {@code DefaultMessage} 是 {@link Http.Message} 的默认实现：构造期快照请求方式、
 * 建立请求头视图与地址对象并持有，三个访问器重复访问返回同一值。
 *
 * <p>请求方式取请求行的方法字面量；头视图实时委托请求的头集合；地址对象构造期快照
 * 地址字符串，查询参数解码使用传入的缺省字符集。
 */
public final class DefaultMessage implements Http.Message {

    /** 构造期快照的请求方式字面量 */
    private final String method;

    /** 请求头只读视图 */
    private final DefaultHeaders headers;

    /** 请求地址 */
    private final DefaultURL url;

    /**
     * 从请求建立消息视图。
     *
     * @param request      请求对象，不能为空
     * @param queryCharset 查询参数解码的缺省字符集，不能为空
     * @throws NullPointerException 当 {@code request} 或 {@code queryCharset} 为 {@code null} 时抛出
     */
    public DefaultMessage(FullHttpRequest request, Charset queryCharset) {
        Objects.requireNonNull(request, "request can not be null");
        this.method = request.method().name();
        this.headers = new DefaultHeaders(request);
        this.url = new DefaultURL(request, queryCharset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String method() {
        return method;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultHeaders headers() {
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultURL url() {
        return url;
    }
}
