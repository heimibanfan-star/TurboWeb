package top.turboweb.http.core.cookie;

import top.turboweb.rest.HttpCookie;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 默认的 {@link HttpCookie} 实现：以字段承载 Cookie 的名称、值，以及路径、域、
 * 最大存活时间、HttpOnly 四项可选属性。
 */
public class DefaultHttpCookie implements HttpCookie {
    /**
     * Cookie 的名称（键），用于标识 Cookie。
     * 不能为空且不能包含控制字符。
     */
    private final String key;

    /**
     * Cookie 的值，与名称对应。
     * 可以为空字符串，但不能为 null。
     */
    private final String value;

    /**
     * Cookie 的路径属性，指定该 Cookie 作用的路径范围。
     * 默认为 "/"，表示根路径及其所有子路径。
     * 浏览器仅在请求路径匹配该路径时发送该 Cookie。
     */
    private String path = "/";

    /**
     * Cookie 的域属性，指定该 Cookie 作用的域名。
     * 默认为当前域名，可以设置为子域通配。
     * 例如设置为 ".example.com" 可供 example.com 及其所有子域使用。
     */
    private String domain;

    /**
     * Cookie 的最大存活时间（单位：秒）。
     * 值为负数（通常是 -1）时，表示会话 Cookie，浏览器关闭后失效。
     * 非负值表示从设置时刻开始的存活秒数，超过后浏览器自动删除。
     */
    private long maxAge = -1;

    /**
     * 是否设置为 HttpOnly 属性。
     * HttpOnly 为 true 时，客户端 JavaScript 无法访问该 Cookie，
     * 能有效防止 XSS 攻击中脚本窃取 Cookie。
     */
    private boolean httpOnly = false;

    public DefaultHttpCookie(String key, String value) {
        checkKey(key);
        Objects.requireNonNull(value, "value can not be null");
        this.key = key;
        this.value = value;
    }

    private void checkKey(String key) {
        Objects.requireNonNull(key, "key can not be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key can not be empty");
        }
        // 你可以根据需要增加更严格的控制字符校验
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setPath(String path) {
        if (path != null && !path.isEmpty()) {
            this.path = path;
        }
    }

    @Override
    public void setDomain(String domain) {
        if (domain != null && !domain.isEmpty()) {
            this.domain = domain;
        }
    }

    @Override
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    /**
     * 转换为符合 Set-Cookie 头的字符串表示。
     * 会对 Cookie 值按给定字符集进行 URL 编码，防止特殊字符导致解析错误。
     *
     * @param charset Cookie 值 URL 编码字符集（写出侧以装配期判定的缺省字符集传入），不能为null
     * @return Set-Cookie 头字符串
     * @throws NullPointerException charset 为 null
     */
    @Override
    public String toSetCookie(Charset charset) {
        Objects.requireNonNull(charset, "charset can not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(key).append("=");

        // 对 value 进行简单 URL 编码
        String encodedValue = URLEncoder.encode(value, charset);
        sb.append(encodedValue);

        if (path != null && !path.isEmpty()) {
            sb.append("; Path=").append(path);
        }

        if (domain != null && !domain.isEmpty()) {
            sb.append("; Domain=").append(domain);
        }

        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }

        if (httpOnly) {
            sb.append("; HttpOnly");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "DefaultHttpCookie{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", path='" + path + '\'' +
                ", domain='" + domain + '\'' +
                ", maxAge=" + maxAge +
                ", httpOnly=" + httpOnly +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultHttpCookie that)) return false;
        return maxAge == that.maxAge &&
                httpOnly == that.httpOnly &&
                key.equals(that.key) &&
                value.equals(that.value) &&
                Objects.equals(path, that.path) &&
                Objects.equals(domain, that.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, path, domain, maxAge, httpOnly);
    }
}
