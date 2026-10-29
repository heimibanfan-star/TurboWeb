package top.turboweb.http.core.uri;

import java.net.IDN;
import java.net.URISyntaxException;

/**
 * 授权组件中的主机值对象：主机名加端口。
 * <p>
 * 解析支持方括号 IPv6 字面量与 {@code host:port} 形态；Punycode
 * 名称在构造时转换为 Unicode 形式。相等性按小写主机名与端口判定。
 */
final class Host {

    private static final char SCOPE_ID_DELIMITER = '%';
    private static final char COLON_CHAR = ':';

    /** IPv6 地址最多 7 个冒号（即 8 个字段） */
    private static final int MAX_COLON_COUNT = 7;

    /** 标准形 IPv6：恰好 8 个字段，每字段 1 到 4 个十六进制字符 */
    private static final java.util.regex.Pattern IPV6_STD_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){7}$");

    /** 压缩形 IPv6：一个"::"，两侧各为 0 到 6 个 1 到 4 位十六进制字段 */
    private static final java.util.regex.Pattern IPV6_HEX_COMPRESSED_PATTERN =
            java.util.regex.Pattern.compile(
                    "^(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)"
                            + "::"
                            + "(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)$");

    /** 作用域 ID：非空的字母、数字或连字符序列 */
    private static final java.util.regex.Pattern SCOPE_ID_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9\\-]+$");

    private final String name;
    private final String lcName;
    private final int port;

    Host(final String name, final int port) {
        super();
        if (name == null) {
            throw new NullPointerException("Host name is null");
        }
        checkPortRange(port);
        this.name = isPunyCode(name) ? IDN.toUnicode(name) : name;
        this.port = port;
        this.lcName = URITextUtil.toLowerCase(this.name);
    }

    /**
     * 从字符序列解析主机与端口。
     * <p>
     * 支持方括号 IPv6 字面量（括号内必须为合法 IPv6 地址）与
     * {@code host:port} 形态；非括号主机的端口部分含冒号视为非法。
     *
     * @param s      待解析字符序列
     * @param cursor 区间游标，解析后推进到停止位置
     * @return 主机值对象
     * @throws URISyntaxException 括号缺失、IPv6 非法或端口非法时抛出
     */
    static Host parse(final CharSequence s, final URITokenizer.Cursor cursor) throws URISyntaxException {
        final String hostName;
        final boolean ipv6Brackets = !cursor.atEnd() && s.charAt(cursor.getPos()) == '[';
        if (ipv6Brackets) {
            cursor.updatePos(cursor.getPos() + 1);
            hostName = URITokenizer.parseContent(s, cursor, URITokenizer.delimiters(']'));
            if (cursor.atEnd() || !(s.charAt(cursor.getPos()) == ']')) {
                throw createException(s, cursor, "Expected an IPv6 closing bracket ']'");
            }
            cursor.updatePos(cursor.getPos() + 1);
            if (!isIPv6(hostName)) {
                throw createException(s, cursor, "Expected an IPv6 address");
            }
        } else {
            hostName = URITokenizer.parseContent(s, cursor, URITokenizer.delimiters('/', '#', '?', ':'));
        }
        String portText = null;
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == ':') {
            cursor.updatePos(cursor.getPos() + 1);
            portText = URITokenizer.parseContent(s, cursor, URITokenizer.delimiters('/', '#', '?'));
        }
        final int port;
        if (!URITextUtil.isBlank(portText)) {
            if (!ipv6Brackets && portText.contains(":")) {
                throw createException(s, cursor, "Expected IPv6 address to be enclosed in brackets");
            }
            try {
                port = Integer.parseInt(portText);
            } catch (final NumberFormatException ex) {
                throw createException(s, cursor, "Port is invalid");
            }
        } else {
            port = -1;
        }
        return new Host(hostName, port);
    }

    /**
     * 将主机格式化为授权形态：IPv6 补方括号，非 ASCII 主机转 Punycode，
     * 端口非缺省时以冒号追加。
     *
     * @param buf  目标缓冲
     * @param host 主机值对象
     */
    static void format(final StringBuilder buf, final Host host) {
        final String hostName = host.getHostName();
        if (isIPv6(hostName)) {
            buf.append('[').append(hostName).append(']');
        } else {
            if (URITextUtil.isAllASCII(hostName)) {
                buf.append(hostName);
            } else {
                buf.append(IDN.toASCII(hostName));
            }
        }
        if (host.getPort() != -1) {
            buf.append(":");
            buf.append(host.getPort());
        }
    }

    /**
     * 从字符序列整体解析主机与端口。
     *
     * @param s 待解析字符序列
     * @return 主机值对象
     * @throws URISyntaxException 括号缺失、IPv6 非法或端口非法时抛出
     */
    static Host parse(final CharSequence s) throws URISyntaxException {
        return parse(s, new URITokenizer.Cursor(0, s.length()));
    }

    private static void checkPortRange(final int port) {
        if (port < -1 || port > 65535) {
            throw new IllegalArgumentException("Port number(Use -1 to specify the scheme default port)");
        }
    }

    private static boolean isPunyCode(final CharSequence s) {
        if (s == null || s.length() < 4) {
            return false;
        }
        return ((s.charAt(0) == 'x' || s.charAt(0) == 'X') &&
                (s.charAt(1) == 'n' || s.charAt(1) == 'N') &&
                s.charAt(2) == '-' &&
                s.charAt(3) == '-');
    }

    private static URISyntaxException createException(
            final CharSequence input, final URITokenizer.Cursor cursor, final String reason) {
        return new URISyntaxException(
                input.subSequence(cursor.getLowerBound(), cursor.getUpperBound()).toString(),
                reason,
                cursor.getPos());
    }

    /**
     * 判断是否为合法 IPv6 地址（标准形、压缩形，含作用域 ID）。
     *
     * @param input 待判定字符序列
     * @return 合法 IPv6 地址为 {@code true}
     */
    static boolean isIPv6(final CharSequence input) {
        int index = -1;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == SCOPE_ID_DELIMITER) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return isIPv6Std(input) || isIPv6HexCompressed(input);
        }
        final CharSequence address = input.subSequence(0, index);
        if (isIPv6Std(address) || isIPv6HexCompressed(address)) {
            final CharSequence scopeId = input.subSequence(index + 1, input.length());
            return scopeId.length() != 0 && SCOPE_ID_PATTERN.matcher(scopeId).matches();
        }
        return false;
    }

    private static boolean isIPv6Std(final CharSequence input) {
        return hasValidIPv6ColonCount(input) && IPV6_STD_PATTERN.matcher(input).matches();
    }

    private static boolean isIPv6HexCompressed(final CharSequence input) {
        return hasValidIPv6ColonCount(input) && IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches();
    }

    private static boolean hasValidIPv6ColonCount(final CharSequence input) {
        int colonCount = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == COLON_CHAR) {
                colonCount++;
            }
        }
        return colonCount >= 2 && colonCount <= MAX_COLON_COUNT;
    }

    public String getHostName() {
        return this.name;
    }

    public int getPort() {
        return this.port;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Host) {
            final Host that = (Host) o;
            return this.lcName.equals(that.lcName) && this.port == that.port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + this.lcName.hashCode();
        hash = hash * 31 + this.port;
        return hash;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        format(buf, this);
        return buf.toString();
    }
}
