package top.turboweb.rest.uri;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * URI 授权组件值对象：用户信息加主机端口。
 * <p>
 * 不可变；主机经 {@link Host} 承载，相等性按用户信息、
 * 小写主机名与端口判定。端口取值范围为 -1（协议缺省）到 65535。
 */
public final class URIAuthority implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = 1L;

    private final String userInfo;
    private final Host host;

    /**
     * 构造授权：用户信息、主机名与端口。
     *
     * @param userInfo 用户信息，可为 {@code null}
     * @param hostname 主机名，不能为 {@code null}
     * @param port     端口，-1 表示协议缺省端口
     * @throws IllegalArgumentException 端口越界时抛出
     */
    public URIAuthority(final String userInfo, final String hostname, final int port) {
        super();
        this.userInfo = userInfo;
        this.host = new Host(hostname, port);
    }

    /**
     * 构造授权：主机名与端口，无用户信息。
     *
     * @param hostname 主机名，不能为 {@code null}
     * @param port     端口，-1 表示协议缺省端口
     * @throws IllegalArgumentException 端口越界时抛出
     */
    public URIAuthority(final String hostname, final int port) {
        this(null, hostname, port);
    }

    /**
     * 构造授权：仅主机名，端口为协议缺省。
     *
     * @param hostname 主机名，不能为 {@code null}
     */
    public URIAuthority(final String hostname) {
        this(null, hostname, -1);
    }

    /**
     * 构造授权：用户信息与既有主机。
     *
     * @param userInfo 用户信息，可为 {@code null}
     * @param host     主机，不能为 {@code null}
     * @throws NullPointerException 主机为 {@code null} 时抛出
     */
    public URIAuthority(final String userInfo, final Host host) {
        super();
        if (host == null) {
            throw new NullPointerException("Host is null");
        }
        this.userInfo = userInfo;
        this.host = host;
    }

    /**
     * 构造授权：复制具名端点的主机名与端口。
     *
     * @param userInfo 用户信息，可为 {@code null}
     * @param endpoint 具名端点，不能为 {@code null}
     * @throws NullPointerException 端点为 {@code null} 时抛出
     */
    public URIAuthority(final String userInfo, final NamedEndpoint endpoint) {
        super();
        if (endpoint == null) {
            throw new NullPointerException("Endpoint is null");
        }
        this.userInfo = userInfo;
        this.host = new Host(endpoint.getHostName(), endpoint.getPort());
    }

    /**
     * 构造授权：复制具名端点的主机名与端口，无用户信息。
     *
     * @param namedEndpoint 具名端点，不能为 {@code null}
     * @throws NullPointerException 端点为 {@code null} 时抛出
     */
    public URIAuthority(final NamedEndpoint namedEndpoint) {
        this(null, namedEndpoint);
    }

    /**
     * 从字符序列按授权语法解析：先分离用户信息，再解析主机端口。
     *
     * @param s      待解析字符序列
     * @param cursor 区间游标，解析后推进到停止位置
     * @return 授权值对象
     * @throws URISyntaxException 主机或端口非法时抛出
     */
    static URIAuthority parse(final CharSequence s, final UriTokenizer.Cursor cursor) throws URISyntaxException {
        String userInfo = null;
        final int initPos = cursor.getPos();
        final String token = UriTokenizer.parseContent(s, cursor, UriTokenizer.delimiters('/', '#', '?', '@'));
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == '@') {
            cursor.updatePos(cursor.getPos() + 1);
            if (!UriTextUtils.isBlank(token)) {
                userInfo = token;
            }
        } else {
            cursor.updatePos(initPos);
        }
        final Host host = Host.parse(s, cursor);
        return new URIAuthority(userInfo, host);
    }

    /**
     * 获取用户信息。
     *
     * @return 用户信息；缺省时为 {@code null}
     */
    public String getUserInfo() {
        return this.userInfo;
    }

    @Override
    public String getHostName() {
        return this.host.getHostName();
    }

    @Override
    public int getPort() {
        return this.host.getPort();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        if (this.userInfo != null) {
            buf.append(this.userInfo);
            buf.append("@");
        }
        Host.format(buf, this);
        return buf.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof URIAuthority) {
            final URIAuthority that = (URIAuthority) obj;
            return Objects.equals(this.userInfo, that.userInfo) &&
                    Objects.equals(this.host, that.host);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + Objects.hashCode(this.userInfo);
        hash = hash * 31 + this.host.hashCode();
        return hash;
    }
}
