package top.turboweb.http.core;

import java.util.Objects;

/**
 * http 地址值对象：以协议、主机与基础路径三个部分组成一个绝对地址。
 *
 * <p>基础路径可为空，地址经 {@link #string()} 以协议开头拼出，路径进料时做预处理：
 * 剥除首尾斜杠静默放行，路径内出现连续斜杠或查询、片段语义字符直接抛出异常；
 * 主机不允许为空，也不允许携带协议、路径或查询、片段语义内容。</p>
 */
public final class HttpAddress {

    private final HttpSchemes schemes;
    private final String host;
    private final String baseUrl;

    /** 完整地址，构造期一次拼出。 */
    private final String address;

    /**
     * 以三部分构造地址，基础路径在此做预处理。
     *
     * @param schemes 协议，不能为 {@code null}
     * @param host    主机，不能为 {@code null} 与空串，且不允许携带协议、路径或查询、片段语义内容
     * @param baseUrl 基础路径，可以为 {@code null} 或以斜杠包裹，首尾斜杠会规整
     * @throws NullPointerException     当 {@code schemes} 或 {@code host} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 {@code host} 为空或携带协议、路径、查询、片段语义内容，
     *         或 {@code baseUrl} 含连续斜杠、查询与片段语义字符时抛出
     */
    public HttpAddress(HttpSchemes schemes, String host, String baseUrl) {
        this.schemes = Objects.requireNonNull(schemes, "schemes must not be null");
        this.host = requireValidHost(host);
        this.baseUrl = normalize(baseUrl);
        this.address = compose();
    }

    /**
     * 以协议、主机与基础路径三段创建地址。
     *
     * @param schemes 协议
     * @param host    主机
     * @param baseUrl 基础路径，可以为 {@code null}
     * @return 地址对象
     */
    public static HttpAddress of(HttpSchemes schemes, String host, String baseUrl) {
        return new HttpAddress(schemes, host, baseUrl);
    }

    /**
     * 以协议与主机两段创建地址，不带基础路径。
     *
     * @param schemes 协议
     * @param host    主机
     * @return 地址对象
     */
    public static HttpAddress of(HttpSchemes schemes, String host) {
        return new HttpAddress(schemes, host, null);
    }

    /**
     * 解析完整地址字符串构造地址：协议头仅接受小写 http 与 https，
     * 协议头之后按第一处斜杠切分主机与基础路径，两侧沿用构造器口径校验与预处理。
     *
     * @param address 完整地址字符串，如 http://example.com/api/v1
     * @return 地址对象
     * @throws NullPointerException     当 {@code address} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当地址缺少协议头、协议取值不受支持、主机为空，
     *         或主机、基础路径携带查询与片段语义内容时抛出
     */
    public static HttpAddress addressOf(String address) {
        Objects.requireNonNull(address, "address must not be null");
        String trimmed = address.trim();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("地址缺少协议头: " + address);
        }
        HttpSchemes schemes = HttpSchemes.of(trimmed.substring(0, schemeEnd));
        String rest = trimmed.substring(schemeEnd + 3);
        int pathStart = rest.indexOf('/');
        if (pathStart < 0) {
            return new HttpAddress(schemes, rest, null);
        }
        return new HttpAddress(schemes, rest.substring(0, pathStart), rest.substring(pathStart));
    }

    /**
     * 拼出完整地址字符串：以协议名开头，结尾不含斜杠。
     * 结果在构造期一次拼出，重复调用返回同一实例。
     *
     * @return 完整地址
     */
    public String string() {
        return address;
    }

    /**
     * 拼出完整地址：baseUrl 为空串时只拼协议与主机，否则以斜杠衔接基础路径。
     */
    private String compose() {
        if (baseUrl.isEmpty()) {
            return schemes.value() + "://" + host;
        }
        return schemes.value() + "://" + host + "/" + baseUrl;
    }
    /**
     * 校验主机：不能为 {@code null} 与空串，且不允许携带协议内容（含 {@code ://}）、
     * 路径斜杠或查询、片段语义字符（含 {@code ?}、{@code &} 与 {@code #}）。
     */
    private static String requireValidHost(String host) {
        Objects.requireNonNull(host, "host must not be null");
        if (host.isEmpty()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (host.contains("://") || host.indexOf('/') >= 0
                || host.indexOf('?') >= 0 || host.indexOf('&') >= 0 || host.indexOf('#') >= 0) {
            throw new IllegalArgumentException("host 不允许携带协议、路径或查询、片段语义内容: " + host);
        }
        return host;
    }

    /**
     * 规整基础路径：空值退化为空串，首尾斜杠静默剥除；
     * 剥除后仍含连续斜杠或查询、片段语义字符的直接抛出异常，规整后为空表示无路径。
     */
    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        int start = 0;
        int end = trimmed.length();
        while (start < end && trimmed.charAt(start) == '/') {
            start++;
        }
        while (end > start && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        String core = trimmed.substring(start, end);
        if (core.contains("//")) {
            throw new IllegalArgumentException("baseUrl 不允许出现连续的斜杠: " + path);
        }
        if (core.indexOf('?') >= 0 || core.indexOf('&') >= 0 || core.indexOf('#') >= 0) {
            throw new IllegalArgumentException("baseUrl 不允许携带查询或片段语义字符: " + path);
        }
        return core;
    }
}