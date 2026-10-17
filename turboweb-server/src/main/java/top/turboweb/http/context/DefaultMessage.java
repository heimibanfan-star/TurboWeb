package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.rest.core.HttpRequestMessage;
import top.turboweb.rest.core.HttpRequestMethod;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@code DefaultMessage} 是 {@link HttpRequestMessage} 的默认实现：构造期快照请求方式、
 * 建立地址对象并持有，两个访问器重复访问返回同一值；请求体内容由装配方解析产出后带入。
 *
 * <p>请求方式取请求行的方法字面量并转化为标准方法枚举；请求头读取实时委托请求的
 * Netty 头集合，不拷贝快照，头名查询大小写不敏感、多值保持与条目计数均由该集合保证；
 * 地址对象构造期快照地址字符串，查询参数解码使用传入的缺省字符集。
 */
public final class DefaultMessage implements HttpRequestMessage {

    /** 构造期快照的请求方式 */
    private final HttpRequestMethod method;

    /** 被委托的 Netty 请求头集合，读取实时生效 */
    private final HttpHeaders headers;

    /** 请求地址 */
    private final DefaultURL url;

    /** 请求体内容 */
    private final HttpContent content;

    /**
     * 从请求建立消息视图。
     *
     * @param request      请求对象，不能为空
     * @param content      请求体内容，不能为空
     * @param queryCharset 查询参数解码的缺省字符集，不能为空
     * @throws NullPointerException     当 {@code request}、{@code content} 或 {@code queryCharset} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 请求行的方法字面量不在标准方法范围内时抛出
     */
    public DefaultMessage(FullHttpRequest request, HttpContent content, Charset queryCharset) {
        Objects.requireNonNull(request, "request can not be null");
        this.method = HttpRequestMethod.valueOf(request.method().name());
        this.headers = request.headers();
        this.url = new DefaultURL(request, queryCharset);
        this.content = Objects.requireNonNull(content, "content can not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpRequestMethod method() {
        return method;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultURL url() {
        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpContent content() {
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String header(String name) {
        return headers.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> headerValues(String name) {
        return headers.getAll(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return headers.contains(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> headerNames() {
        return headers.names();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int headerSize() {
        return headers.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean headerIsEmpty() {
        return headers.isEmpty();
    }
}
