package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.core.helper.UrlHelper;
import top.turboweb.rest.core.HttpUrl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code DefaultURL} 是 {@link HttpUrl} 的默认实现：构造期从请求快照地址
 * 字符串与缺省字符集，不持有请求对象，构造返回后调用方可立即释放请求；查询参数
 * 不即时解析，由首访 {@link #queryParams()} 时按 {@link UrlHelper} 的纯百分号解码
 * 口径建立并缓存，此后复用。
 *
 * <p>本类按请求单线程使用，缓存建立不加同步。
 */
public final class DefaultURL implements HttpUrl {

    /** 请求行原始地址的快照 */
    private final String uri;

    /** 查询段转义字节的缺省解码字符集 */
    private final Charset queryCharset;

    /** 查询参数缓存：首个访问调用时建立 */
    private Map<String, List<String>> queryParams;

    /**
     * 从请求快照地址。
     *
     * @param request      请求对象，不能为空；其请求行 URI 复制为字符串持有
     * @param queryCharset 查询参数百分号解码的缺省字符集，不能为空
     * @throws NullPointerException 当 {@code request} 或 {@code queryCharset} 为 {@code null} 时抛出
     */
    public DefaultURL(FullHttpRequest request, Charset queryCharset) {
        Objects.requireNonNull(request, "request can not be null");
        this.uri = request.uri();
        this.queryCharset = Objects.requireNonNull(queryCharset, "queryCharset can not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String url(boolean pathOnly) {
        if (!pathOnly) {
            return uri;
        }
        int cut = uri.length();
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            cut = queryIndex;
        }
        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex >= 0 && fragmentIndex < cut) {
            cut = fragmentIndex;
        }
        return uri.substring(0, cut);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> queryParams() {
        if (queryParams == null) {
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            for (UrlHelper.ParamPair param : UrlHelper.parseQueryParams(uri, queryCharset)) {
                grouped.computeIfAbsent(param.getName(), k -> new ArrayList<>(1)).add(param.getValue());
            }
            // 外层映射与内层值列表各包不可变视图：值可为 null，不能用 List.copyOf
            Map<String, List<String>> frozen = new LinkedHashMap<>();
            grouped.forEach((name, values) -> frozen.put(name, Collections.unmodifiableList(values)));
            queryParams = Collections.unmodifiableMap(frozen);
        }
        return queryParams;
    }
}
