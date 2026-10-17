package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import top.turboweb.rest.Http;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@code DefaultHeaders} 是 {@link Http.Headers} 的默认实现：实时委托 Netty
 * 的 {@link HttpHeaders} 集合，不拷贝快照；头名大小写不敏感的查询口径、多值保持与
 * 条目计数均由 Netty 视图保证，在请求头集合的可见生命周期内均可读取。
 *
 * <p>本视图只读，不提供任何写通道；常用请求头便捷方法继承接口默认实现，
 * 经由 {@link #get(String)} 落到委托集合。
 */
public final class DefaultHeaders implements Http.Headers {

    /** 被委托的 Netty 请求头集合 */
    private final HttpHeaders headers;

    /**
     * 建立请求头的只读视图。
     *
     * @param request 请求对象，不能为空；视图随其请求头集合的变动而变动
     * @throws NullPointerException 当 {@code request} 为 {@code null} 时抛出
     */
    public DefaultHeaders(FullHttpRequest request) {
        Objects.requireNonNull(request, "request can not be null");
        this.headers = request.headers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String get(String name) {
        return headers.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAll(String name) {
        return headers.getAll(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String name) {
        return headers.contains(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> names() {
        return headers.names();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return headers.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }
}
