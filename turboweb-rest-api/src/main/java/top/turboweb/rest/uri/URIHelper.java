package top.turboweb.rest.uri;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link URI} 构建器：按组件（协议、授权、路径、查询、片段）逐步组装
 * URI，或在既有 URI 基础上修改后重建。
 * <p>
 * 组件语义口径：
 * <ul>
 *  <li>从既有 URI 消化时，编码原样（百分号形态）与解码值同时保留；
 *      未触碰的组件在构建时原样输出，被修改的组件重新编码。</li>
 *  <li>路径以段为单位管理，构建时逐段按非保留字集编码；无根路径
 *      （不以前导斜杠开头）在无授权时省略前导斜杠。</li>
 *  <li>查询参数与自定义查询互斥：设置一方会清除另一方。</li>
 *  <li>字符集缺省为 UTF-8，可通过 {@link #setCharset(Charset)} 调整，
 *      影响后续组件的百分号编码与解码。</li>
 * </ul>
 * <p>
 * 构建时 http 与 https 协议要求主机名非空，否则抛出
 * {@link URISyntaxException}。
 */
public class URIHelper {

    private static final char QUERY_PARAM_SEPARATOR = '&';
    private static final char PARAM_VALUE_SEPARATOR = '=';
    private static final char PATH_SEPARATOR = '/';

    private String scheme;
    private String encodedSchemeSpecificPart;
    private String encodedAuthority;
    private String host;
    private int port;
    private String encodedPath;
    private boolean pathRootless;
    private List<String> pathSegments;
    private String encodedQuery;
    private List<ParamPair> queryParams;
    private String query;
    private Charset charset;
    private String fragment;
    private String encodedFragment;

    /**
     * 构造空实例。
     */
    public URIHelper() {
        super();
        this.port = -1;
    }

    /**
     * 从合法 URI 字符串构造实例，字符集为 UTF-8。
     *
     * @param uriString 合法的 URI 字符串
     * @throws URISyntaxException 输入不是合法 URI 时抛出
     */
    public URIHelper(final String uriString) throws URISyntaxException {
        this(new URI(uriString), StandardCharsets.UTF_8);
    }

    /**
     * 从 {@link URI} 对象构造实例，字符集为 UTF-8。
     *
     * @param uri URI 对象
     */
    public URIHelper(final URI uri) {
        this(uri, StandardCharsets.UTF_8);
    }

    /**
     * 从合法 URI 字符串构造实例。
     *
     * @param uriString 合法的 URI 字符串
     * @param charset   组件解码字符集
     * @throws URISyntaxException 输入不是合法 URI 时抛出
     */
    public URIHelper(final String uriString, final Charset charset) throws URISyntaxException {
        this(new URI(uriString), charset);
    }

    /**
     * 从 {@link URI} 对象构造实例。
     *
     * @param uri     URI 对象
     * @param charset 组件解码字符集
     */
    public URIHelper(final URI uri, final Charset charset) {
        super();
        digestURI(uri, charset);
    }

    /**
     * 以本机地址为主机构造实例。
     *
     * @return 新实例
     * @throws UnknownHostException 本机主机名无法解析时抛出
     */
    public static URIHelper localhost() throws UnknownHostException {
        return new URIHelper().setHost(InetAddress.getLocalHost());
    }

    /**
     * 以环回地址为主机构造实例。
     *
     * @return 新实例
     */
    public static URIHelper loopbackAddress() {
        return new URIHelper().setHost(InetAddress.getLoopbackAddress());
    }

    /**
     * 解析查询序列为名值对列表：以 {@code &} 分隔参数、以首个 {@code =}
     * 分隔名称与值；空名参数跳过，无等号参数值为 {@code null}；
     * 名称与值各自百分号解码，空白序列折叠为单个空格。
     *
     * @param s       查询序列，可为 {@code null}
     * @param charset 解码字符集
     * @return 名值对列表；输入为 {@code null} 时返回 {@code null}
     */
    static List<ParamPair> parseQuery(final CharSequence s, final Charset charset) {
        if (s == null) {
            return null;
        }
        final URITokenizer.Cursor cursor = new URITokenizer.Cursor(0, s.length());
        final List<ParamPair> list = new ArrayList<>();
        while (!cursor.atEnd()) {
            final String name = URITokenizer.parseToken(s, cursor, URITokenizer.delimiters(QUERY_PARAM_SEPARATOR, PARAM_VALUE_SEPARATOR));
            String value = null;
            if (!cursor.atEnd()) {
                final int delim = s.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (delim == PARAM_VALUE_SEPARATOR) {
                    value = URITokenizer.parseToken(s, cursor, URITokenizer.delimiters(QUERY_PARAM_SEPARATOR));
                    if (!cursor.atEnd()) {
                        cursor.updatePos(cursor.getPos() + 1);
                    }
                }
            }
            if (!name.isEmpty()) {
                list.add(new ParamPair(
                        PercentCodec.decode(name, charset),
                        PercentCodec.decode(value, charset)));
            }
        }
        return list;
    }

    /**
     * 按斜杠切分路径：跳过前导斜杠，连续斜杠产生空段，
     * 结尾斜杠产生空段；不做解码。
     *
     * @param s 路径序列，可为 {@code null}
     * @return 路径段列表；输入为 {@code null} 时返回空列表
     */
    static List<String> splitPath(final CharSequence s) {
        if (s == null) {
            return Collections.emptyList();
        }
        final URITokenizer.Cursor cursor = new URITokenizer.Cursor(0, s.length());
        // 跳过前导斜杠
        if (cursor.atEnd()) {
            return new ArrayList<>(0);
        }
        if (s.charAt(cursor.getPos()) == PATH_SEPARATOR) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        final List<String> list = new ArrayList<>();
        final StringBuilder buf = new StringBuilder();
        for (; ; ) {
            if (cursor.atEnd()) {
                list.add(buf.toString());
                break;
            }
            final char current = s.charAt(cursor.getPos());
            if (current == PATH_SEPARATOR) {
                list.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(current);
            }
            cursor.updatePos(cursor.getPos() + 1);
        }
        return list;
    }

    /**
     * 切分路径并逐段百分号解码。
     *
     * @param s       路径序列，可为 {@code null}
     * @param charset 解码字符集
     * @return 解码后的路径段列表；输入为 {@code null} 时返回空列表
     */
    static List<String> parsePath(final CharSequence s, final Charset charset) {
        if (s == null) {
            return Collections.emptyList();
        }
        final List<String> segments = splitPath(s);
        final List<String> list = new ArrayList<>(segments.size());
        for (final String segment : segments) {
            list.add(PercentCodec.decode(segment, charset));
        }
        return list;
    }

    /**
     * 将路径段编码后写入缓冲：无根形态时不输出前导斜杠。
     *
     * @param buf      目标缓冲
     * @param segments 路径段
     * @param rootless 是否为无根路径
     * @param charset  编码字符集
     */
    static void formatPath(final StringBuilder buf, final Iterable<String> segments, final boolean rootless,
                           final Charset charset) {
        int i = 0;
        for (final String segment : segments) {
            if (i > 0 || !rootless) {
                buf.append(PATH_SEPARATOR);
            }
            PercentCodec.encode(buf, segment, charset);
            i++;
        }
    }

    /**
     * 将查询参数编码后写入缓冲：值为 {@code null} 的参数仅输出名称，
     * 空串值输出名称加等号；名称与值逐项编码。
     *
     * @param buf     目标缓冲
     * @param params  名值对列表
     * @param charset 编码字符集
     */
    static void formatQuery(final StringBuilder buf, final Iterable<? extends ParamPair> params,
                            final Charset charset) {
        int i = 0;
        for (final ParamPair parameter : params) {
            if (i > 0) {
                buf.append(QUERY_PARAM_SEPARATOR);
            }
            PercentCodec.encode(buf, parameter.name(), charset);
            if (parameter.value() != null) {
                buf.append(PARAM_VALUE_SEPARATOR);
                PercentCodec.encode(buf, parameter.value(), charset);
            }
            i++;
        }
    }

    /**
     * 构建当前状态的 {@link URI}。
     *
     * @return 构建结果
     * @throws URISyntaxException http 或 https 协议下主机名为空，
     *                            或组装结果不是合法 URI 时抛出
     */
    public URI build() throws URISyntaxException {
        if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && URITextUtil.isBlank(host)) {
            throw new URISyntaxException(scheme, "http/https URI cannot have an empty host identifier");
        }
        return new URI(buildString());
    }

    private String buildString() {
        final StringBuilder sb = new StringBuilder();
        if (this.scheme != null) {
            sb.append(this.scheme).append(':');
        }
        if (this.encodedSchemeSpecificPart != null) {
            sb.append(this.encodedSchemeSpecificPart);
        } else {
            final boolean authoritySpecified;
            if (this.encodedAuthority != null) {
                sb.append("//").append(this.encodedAuthority);
                authoritySpecified = true;
            } else if (this.host != null) {
                sb.append("//");
                if (isIPv6(this.host)) {
                    sb.append("[").append(this.host).append("]");
                } else {
                    sb.append(PercentCodec.encode(this.host, this.charset));
                }
                if (this.port >= 0) {
                    sb.append(":").append(this.port);
                }
                authoritySpecified = true;
            } else {
                authoritySpecified = false;
            }
            if (this.encodedPath != null) {
                if (authoritySpecified && !URITextUtil.isEmpty(this.encodedPath) && !this.encodedPath.startsWith("/")) {
                    sb.append('/');
                }
                sb.append(this.encodedPath);
            } else if (this.pathSegments != null) {
                formatPath(sb, this.pathSegments, !authoritySpecified && this.pathRootless, this.charset);
            }
            if (this.encodedQuery != null) {
                sb.append("?").append(this.encodedQuery);
            } else if (this.queryParams != null && !this.queryParams.isEmpty()) {
                sb.append("?");
                formatQuery(sb, this.queryParams, this.charset);
            } else if (this.query != null) {
                sb.append("?");
                PercentCodec.encode(sb, this.query, this.charset, PercentCodec.URIC);
            }
        }
        if (this.encodedFragment != null) {
            sb.append("#").append(this.encodedFragment);
        } else if (this.fragment != null) {
            sb.append("#");
            PercentCodec.encode(sb, this.fragment, this.charset);
        }
        return sb.toString();
    }

    private void digestURI(final URI uri, final Charset charset) {
        this.scheme = uri.getScheme();
        this.encodedSchemeSpecificPart = uri.getRawSchemeSpecificPart();
        this.encodedAuthority = uri.getRawAuthority();
        final String uriHost = uri.getHost();
        // java.net.URI 对方括号 IPv6 返回带括号形态；括号属于 URI 编码细节，不属于主机名
        this.host = uriHost != null && isIPv6URLBracketed(uriHost)
                ? uriHost.substring(1, uriHost.length() - 1)
                : uriHost;
        this.port = uri.getPort();
        if (this.encodedAuthority != null && this.host == null) {
            try {
                final URITokenizer.Cursor cursor = new URITokenizer.Cursor(0, this.encodedAuthority.length());
                URITokenizer.parseContent(this.encodedAuthority, cursor,
                        URITokenizer.delimiters('/', '#', '?', '@'));
                if (!cursor.atEnd() && this.encodedAuthority.charAt(cursor.getPos()) == '@') {
                    // RFC 9110 下 HTTP URI 不含用户信息；输入残留用户信息时主机取 @ 之后
                    cursor.updatePos(cursor.getPos() + 1);
                } else {
                    cursor.updatePos(0);
                }
                final Host parsedHost = Host.parse(this.encodedAuthority, cursor);
                this.host = PercentCodec.decode(parsedHost.getHostName(), charset);
                this.port = parsedHost.getPort();
            } catch (final URISyntaxException ignore) {
                // 主机回退解析失败时保留 URI 原样输出能力
            }
        }
        this.encodedPath = uri.getRawPath();
        this.pathSegments = parsePath(uri.getRawPath(), charset);
        this.pathRootless = uri.getRawPath() == null || !uri.getRawPath().startsWith("/");
        this.encodedQuery = uri.getRawQuery();
        this.queryParams = parseQuery(uri.getRawQuery(), charset);
        this.encodedFragment = uri.getRawFragment();
        this.fragment = uri.getFragment();
        this.charset = charset;
    }

    /**
     * 设置协议。空白值视为清除。
     *
     * @param scheme 协议名
     * @return 本实例
     */
    public URIHelper setScheme(final String scheme) {
        this.scheme = !URITextUtil.isBlank(scheme) ? scheme : null;
        return this;
    }

    /**
     * 设置 URI 固定部分（scheme specific part）的编码原样：
     * 构建时按给定内容直接输出，不再重新编码。
     *
     * @param schemeSpecificPart 固定部分编码原样
     * @return 本实例
     */
    public URIHelper setSchemeSpecificPart(final String schemeSpecificPart) {
        this.encodedSchemeSpecificPart = schemeSpecificPart;
        return this;
    }

    /**
     * 设置 URI 固定部分，并附加若干查询参数。
     *
     * @param schemeSpecificPart 固定部分；空白值视为清除
     * @param nvps               查询参数，可为 {@code null}
     * @return 本实例
     */
    public URIHelper setSchemeSpecificPart(final String schemeSpecificPart, final ParamPair... nvps) {
        return setSchemeSpecificPart(schemeSpecificPart, nvps != null ? Arrays.asList(nvps) : null);
    }

    /**
     * 设置 URI 固定部分，并附加查询参数列表：非空参数列表以
     * {@code ?} 追加到固定部分之后，整体作为编码原样输出。
     *
     * @param schemeSpecificPart 固定部分；空白值视为清除
     * @param nvps               查询参数，可为 {@code null}
     * @return 本实例
     */
    public URIHelper setSchemeSpecificPart(final String schemeSpecificPart, final List<ParamPair> nvps) {
        this.encodedSchemeSpecificPart = null;
        if (!URITextUtil.isBlank(schemeSpecificPart)) {
            final StringBuilder sb = new StringBuilder(schemeSpecificPart);
            if (nvps != null && !nvps.isEmpty()) {
                sb.append("?");
                formatQuery(sb, nvps, this.charset);
            }
            this.encodedSchemeSpecificPart = sb.toString();
        }
        return this;
    }

    /**
     * 设置主机。输入应为未编码形态：IPv6 地址不带方括号；
     * 构建时 IPv6 地址补方括号，其余按非保留字集编码。
     *
     * @param host 主机地址对象
     * @return 本实例
     */
    public URIHelper setHost(final InetAddress host) {
        this.host = host != null ? host.getHostAddress() : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * 设置主机。输入应为未编码形态：IPv6 地址不带方括号。
     *
     * @param host 主机名
     * @return 本实例
     */
    public URIHelper setHost(final String host) {
        this.host = host;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * 设置端口。负值归一为 -1（协议缺省）。
     *
     * @param port 端口
     * @return 本实例
     */
    public URIHelper setPort(final int port) {
        this.port = port < 0 ? -1 : port;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * 设置路径。值应为未编码形态，可含非 ASCII 字符；
     * 以斜杠切分为路径段，前导斜杠决定有根形态。
     *
     * @param path 路径
     * @return 本实例
     */
    public URIHelper setPath(final String path) {
        setPathSegments(path != null ? splitPath(path) : null);
        this.pathRootless = path != null && !path.startsWith("/");
        return this;
    }

    /**
     * 在既有路径之后追加路径。值应为未编码形态。
     *
     * @param path 路径
     * @return 本实例
     */
    public URIHelper appendPath(final String path) {
        if (path != null) {
            appendPathSegments(splitPath(path));
        }
        return this;
    }

    /**
     * 以路径段设置路径。
     *
     * @param pathSegments 路径段
     * @return 本实例
     */
    public URIHelper setPathSegments(final String... pathSegments) {
        return setPathSegments(Arrays.asList(pathSegments));
    }

    /**
     * 追加路径段。
     *
     * @param pathSegments 路径段
     * @return 本实例
     */
    public URIHelper appendPathSegments(final String... pathSegments) {
        return appendPathSegments(Arrays.asList(pathSegments));
    }

    /**
     * 以路径段设置无根路径（不输出前导斜杠）。
     *
     * @param pathSegments 路径段
     * @return 本实例
     */
    public URIHelper setPathSegmentsRootless(final String... pathSegments) {
        return setPathSegmentsRootless(Arrays.asList(pathSegments));
    }

    /**
     * 以路径段列表设置路径；空列表或 {@code null} 清空路径，
     * 路径归为有根形态。
     *
     * @param pathSegments 路径段列表
     * @return 本实例
     */
    public URIHelper setPathSegments(final List<String> pathSegments) {
        this.pathSegments = pathSegments != null && !pathSegments.isEmpty() ? new ArrayList<>(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        this.pathRootless = false;
        return this;
    }

    /**
     * 追加路径段列表；空列表或 {@code null} 不改变现状。
     *
     * @param pathSegments 路径段列表
     * @return 本实例
     */
    public URIHelper appendPathSegments(final List<String> pathSegments) {
        if (pathSegments != null && !pathSegments.isEmpty()) {
            if (this.pathSegments == null) {
                this.pathSegments = new ArrayList<>();
            }
            this.pathSegments.addAll(pathSegments);
            this.encodedSchemeSpecificPart = null;
            this.encodedPath = null;
        }
        return this;
    }

    /**
     * 以路径段列表设置无根路径（不输出前导斜杠）；
     * 空列表或 {@code null} 清空路径。
     *
     * @param pathSegments 路径段列表
     * @return 本实例
     */
    public URIHelper setPathSegmentsRootless(final List<String> pathSegments) {
        this.pathSegments = pathSegments != null && !pathSegments.isEmpty() ? new ArrayList<>(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        this.pathRootless = true;
        return this;
    }

    /**
     * 移除查询：参数列表、自定义查询与编码原样一并清除。
     *
     * @return 本实例
     */
    public URIHelper removeQuery() {
        this.queryParams = null;
        this.query = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * 设置查询参数列表（整体替换）。参数名称与值应为未编码形态。
     *
     * @param nameValuePairs 名值对列表，可为 {@code null}（清空）
     * @return 本实例
     */
    public URIHelper setParameters(final List<ParamPair> nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        if (nameValuePairs != null) {
            this.queryParams.addAll(nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 追加查询参数列表。参数名称与值应为未编码形态。
     *
     * @param nameValuePairs 名值对列表，可为 {@code null}
     * @return 本实例
     */
    public URIHelper addParameters(final List<ParamPair> nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (nameValuePairs != null) {
            this.queryParams.addAll(nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 设置查询参数（整体替换）。参数名称与值应为未编码形态。
     *
     * @param nameValuePairs 名值对，可为 {@code null}（清空）
     * @return 本实例
     */
    public URIHelper setParameters(final ParamPair... nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        if (nameValuePairs != null) {
            Collections.addAll(this.queryParams, nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 追加一个查询参数。参数名称与值应为未编码形态。
     *
     * @param param 参数名称
     * @param value 参数值，可为 {@code null}
     * @return 本实例
     */
    public URIHelper addParameter(final String param, final String value) {
        return addParameter(new ParamPair(param, value));
    }

    /**
     * 追加一个查询参数；{@code null} 参数不改变现状。
     *
     * @param nameValuePair 名值对
     * @return 本实例
     */
    public URIHelper addParameter(final ParamPair nameValuePair) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (nameValuePair != null) {
            this.queryParams.add(nameValuePair);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 移除指定名称的全部查询参数；即使未命中也清除自定义查询与编码原样。
     *
     * @param param 参数名称，不能为 {@code null}
     * @return 本实例
     * @throws NullPointerException 参数名称为 {@code null} 时抛出
     */
    public URIHelper removeParameter(final String param) {
        if (param == null) {
            throw new NullPointerException("param is null");
        }
        if (this.queryParams != null && !this.queryParams.isEmpty()) {
            this.queryParams.removeIf(nvp -> nvp.name().equals(param));
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 设置查询参数：移除同名旧值后将新值追加至尾部。
     * 参数名称与值应为未编码形态。
     *
     * @param param 参数名称
     * @param value 参数值，可为 {@code null}
     * @return 本实例
     */
    public URIHelper setParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (!this.queryParams.isEmpty()) {
            this.queryParams.removeIf(nvp -> nvp.name().equals(param));
        }
        this.queryParams.add(new ParamPair(param, value));
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * 清空查询参数。
     *
     * @return 本实例
     */
    public URIHelper clearParameters() {
        this.queryParams = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * 设置自定义查询。值应为未编码形态，构建时按 URI 合法字集编码
     * （子分隔符直通）。空白值视为清除。
     *
     * @param query 自定义查询
     * @return 本实例
     */
    public URIHelper setCustomQuery(final String query) {
        this.query = !URITextUtil.isBlank(query) ? query : null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.queryParams = null;
        return this;
    }

    /**
     * 设置片段。值应为未编码形态；空白值视为清除。
     *
     * @param fragment 片段
     * @return 本实例
     */
    public URIHelper setFragment(final String fragment) {
        this.fragment = !URITextUtil.isBlank(fragment) ? fragment : null;
        this.encodedFragment = null;
        return this;
    }

    /**
     * 设置组件编码字符集，影响后续组件的百分号编码与解码。
     *
     * @param charset 字符集
     * @return 本实例
     */
    public URIHelper setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    /**
     * 获取组件编码字符集。
     *
     * @return 字符集；未设置时为 {@code null}（构建按 UTF-8 兜底）
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 判断 URI 是否为绝对 URI（协议已设置）。
     *
     * @return 绝对 URI 为 {@code true}
     */
    public boolean isAbsolute() {
        return this.scheme != null;
    }

    /**
     * 判断 URI 是否为不透明 URI（无路径组件）。
     *
     * @return 不透明 URI 为 {@code true}
     */
    public boolean isOpaque() {
        return this.pathSegments == null && this.encodedPath == null;
    }

    /**
     * 获取协议。
     *
     * @return 协议；未设置时为 {@code null}
     */
    public String getScheme() {
        return this.scheme;
    }

    /**
     * 获取固定部分的编码原样。
     *
     * @return 固定部分编码原样；未设置时为 {@code null}
     */
    public String getSchemeSpecificPart() {
        return this.encodedSchemeSpecificPart;
    }

    /**
     * 获取主机。IPv6 地址为不带方括号的形态（与 {@link URI#getHost()}
     * 的带方括号行为不同）。
     *
     * @return 主机；未设置时为 {@code null}
     */
    public String getHost() {
        return this.host;
    }

    /**
     * 获取端口。
     *
     * @return 端口；未设置时为 {@code -1}
     */
    public int getPort() {
        return this.port;
    }

    /**
     * 判断路径是否为空。
     *
     * @return 路径为空为 {@code true}
     */
    public boolean isPathEmpty() {
        return (this.pathSegments == null || this.pathSegments.isEmpty()) &&
                (this.encodedPath == null || this.encodedPath.isEmpty());
    }

    /**
     * 获取路径段（解码形态）。
     *
     * @return 路径段列表的副本；无路径时为空列表
     */
    public List<String> getPathSegments() {
        return this.pathSegments != null ? new ArrayList<>(this.pathSegments) : new ArrayList<>();
    }

    /**
     * 获取路径：各段以前导斜杠拼接的解码形态，不做百分号编码。
     *
     * @return 路径；无路径段时为 {@code null}
     */
    public String getPath() {
        if (this.pathSegments == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        for (final String segment : this.pathSegments) {
            result.append('/').append(segment);
        }
        return result.toString();
    }

    /**
     * 判断查询是否为空。
     *
     * @return 查询为空为 {@code true}
     */
    public boolean isQueryEmpty() {
        return (this.queryParams == null || this.queryParams.isEmpty()) && this.encodedQuery == null;
    }

    /**
     * 获取查询参数列表（解码形态）。
     *
     * @return 名值对列表的副本；无查询时为空列表
     */
    public List<ParamPair> getQueryParams() {
        return this.queryParams != null ? new ArrayList<>(this.queryParams) : new ArrayList<>();
    }

    /**
     * 获取指定名称的第一个查询参数。
     *
     * @param name 参数名称
     * @return 首个同名名值对；不存在或无查询时为 {@code null}
     */
    public ParamPair getFirstQueryParam(final String name) {
        if (queryParams == null) {
            return null;
        }
        return queryParams.stream().filter(e -> name.equals(e.name())).findFirst().orElse(null);
    }

    /**
     * 获取片段。
     *
     * @return 片段；未设置时为 {@code null}
     */
    public String getFragment() {
        return this.fragment;
    }

    /**
     * 优化 URI：协议与主机转小写、消除路径点段（保持结尾斜杠）、
     * 组件编码重算；无根路径时仅协议转小写。适用于有根路径的非不透明 URI。
     *
     * @return 本实例
     */
    public URIHelper optimize() {
        final String scheme = this.scheme;
        if (scheme != null) {
            this.scheme = URITextUtil.toLowerCase(scheme);
        }

        if (this.pathRootless) {
            return this;
        }

        // 令各组件编码原样失效，构建时重新编码
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        this.encodedPath = null;
        this.encodedQuery = null;
        this.encodedFragment = null;

        final String host = this.host;
        if (host != null) {
            this.host = URITextUtil.toLowerCase(host);
        }

        if (this.pathSegments != null) {
            final List<String> inputSegments = this.pathSegments;
            if (!inputSegments.isEmpty()) {
                final LinkedList<String> outputSegments = new LinkedList<>();
                for (final String inputSegment : inputSegments) {
                    if (!inputSegment.isEmpty() && !".".equals(inputSegment)) {
                        if ("..".equals(inputSegment)) {
                            if (!outputSegments.isEmpty()) {
                                outputSegments.removeLast();
                            }
                        } else {
                            outputSegments.addLast(inputSegment);
                        }
                    }
                }
                if (!inputSegments.isEmpty()) {
                    final String lastSegment = inputSegments.get(inputSegments.size() - 1);
                    if (lastSegment.isEmpty()) {
                        outputSegments.addLast("");
                    }
                }
                this.pathSegments = outputSegments;
            } else {
                this.pathSegments = Collections.singletonList("");
            }
        }

        return this;
    }

    /**
     * 等价于 {@link #optimize()}。
     *
     * @return 本实例
     * @deprecated 语义上与 {@link #optimize()} 相同，后者命名更准确
     */
    @Deprecated
    public URIHelper normalizeSyntax() {
        return optimize();
    }

    /**
     * 将当前状态转为 URI 字符串（与 {@link #build()} 的输入同形，
     * 不做合法性校验）。
     *
     * @return URI 字符串
     */
    @Override
    public String toString() {
        return buildString();
    }

    private static boolean isIPv6(final String host) {
        return Host.isIPv6(host);
    }

    /**
     * 判断输入是否为方括号包裹的 IPv6 地址字面量（仅匹配 {@code [::1]} 形态）。
     *
     * @param input 待判定字符序列
     * @return 方括号 IPv6 形态为 {@code true}
     */
    static boolean isIPv6URLBracketed(final CharSequence input) {
        if (input.length() == 0) {
            return false;
        }
        return input.charAt(0) == '['
                && input.charAt(input.length() - 1) == ']'
                && Host.isIPv6(input.subSequence(1, input.length() - 1));
    }
}
