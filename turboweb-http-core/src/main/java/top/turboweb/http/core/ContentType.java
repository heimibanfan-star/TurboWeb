package top.turboweb.http.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * {@code ContentType} 表示 HTTP 内容类型值对象：一个 MIME 类型加一个可选字符集。
 * <p>
 * 不可变。字符集允许缺省（{@link #getCharset()} 返回 {@code null}），
 * 缺省时由消费方按自身约定回落默认字符集。
 * <p>
 * 本类内置常用标准媒体类型常量；任意其它 MIME 类型可由
 * {@link #create(String)} / {@link #create(String, Charset)} 构造。
 */
public final class ContentType {

    /** {@code application/atom+xml}，字符集 UTF-8 */
    public static final ContentType APPLICATION_ATOM_XML =
            create("application/atom+xml", StandardCharsets.UTF_8);

    /** {@code application/x-www-form-urlencoded}，字符集 ISO-8859-1 */
    public static final ContentType APPLICATION_FORM_URLENCODED =
            create("application/x-www-form-urlencoded", StandardCharsets.ISO_8859_1);

    /** {@code application/json}，字符集 UTF-8 */
    public static final ContentType APPLICATION_JSON =
            create("application/json", StandardCharsets.UTF_8);

    /** {@code application/x-ndjson}，字符集 UTF-8 */
    public static final ContentType APPLICATION_NDJSON =
            create("application/x-ndjson", StandardCharsets.UTF_8);

    /** {@code application/octet-stream}，不带字符集 */
    public static final ContentType APPLICATION_OCTET_STREAM =
            create("application/octet-stream");

    /** {@code application/pdf}，字符集 UTF-8 */
    public static final ContentType APPLICATION_PDF =
            create("application/pdf", StandardCharsets.UTF_8);

    /** {@code application/problem+json}，字符集 UTF-8 */
    public static final ContentType APPLICATION_PROBLEM_JSON =
            create("application/problem+json", StandardCharsets.UTF_8);

    /** {@code application/problem+xml}，字符集 UTF-8 */
    public static final ContentType APPLICATION_PROBLEM_XML =
            create("application/problem+xml", StandardCharsets.UTF_8);

    /** {@code application/rss+xml}，字符集 UTF-8 */
    public static final ContentType APPLICATION_RSS_XML =
            create("application/rss+xml", StandardCharsets.UTF_8);

    /** {@code application/soap+xml}，字符集 UTF-8 */
    public static final ContentType APPLICATION_SOAP_XML =
            create("application/soap+xml", StandardCharsets.UTF_8);

    /** {@code application/svg+xml}，不带字符集 */
    public static final ContentType APPLICATION_SVG_XML =
            create("application/svg+xml");

    /** {@code application/xhtml+xml}，不带字符集 */
    public static final ContentType APPLICATION_XHTML_XML =
            create("application/xhtml+xml");

    /** {@code application/xml}，字符集 UTF-8 */
    public static final ContentType APPLICATION_XML =
            create("application/xml", StandardCharsets.UTF_8);

    /** {@code image/bmp}，不带字符集 */
    public static final ContentType IMAGE_BMP = create("image/bmp");

    /** {@code image/gif}，不带字符集 */
    public static final ContentType IMAGE_GIF = create("image/gif");

    /** {@code image/jpeg}，不带字符集 */
    public static final ContentType IMAGE_JPEG = create("image/jpeg");

    /** {@code image/png}，不带字符集 */
    public static final ContentType IMAGE_PNG = create("image/png");

    /** {@code image/svg+xml}，不带字符集 */
    public static final ContentType IMAGE_SVG = create("image/svg+xml");

    /** {@code image/tiff}，不带字符集 */
    public static final ContentType IMAGE_TIFF = create("image/tiff");

    /** {@code image/webp}，不带字符集 */
    public static final ContentType IMAGE_WEBP = create("image/webp");

    /** {@code multipart/form-data}，字符集 ISO-8859-1 */
    public static final ContentType MULTIPART_FORM_DATA =
            create("multipart/form-data", StandardCharsets.ISO_8859_1);

    /** {@code multipart/mixed}，字符集 ISO-8859-1 */
    public static final ContentType MULTIPART_MIXED =
            create("multipart/mixed", StandardCharsets.ISO_8859_1);

    /** {@code multipart/related}，字符集 ISO-8859-1 */
    public static final ContentType MULTIPART_RELATED =
            create("multipart/related", StandardCharsets.ISO_8859_1);

    /** {@code text/event-stream}，字符集 UTF-8 */
    public static final ContentType TEXT_EVENT_STREAM =
            create("text/event-stream", StandardCharsets.UTF_8);

    /** {@code text/html}，字符集 UTF-8 */
    public static final ContentType TEXT_HTML =
            create("text/html", StandardCharsets.UTF_8);

    /** {@code text/markdown}，字符集 UTF-8 */
    public static final ContentType TEXT_MARKDOWN =
            create("text/markdown", StandardCharsets.UTF_8);

    /** {@code text/plain}，字符集 UTF-8 */
    public static final ContentType TEXT_PLAIN =
            create("text/plain", StandardCharsets.UTF_8);

    /** {@code text/xml}，字符集 UTF-8 */
    public static final ContentType TEXT_XML =
            create("text/xml", StandardCharsets.UTF_8);

    /** {@code *}{@code /}，不带字符集 */
    public static final ContentType WILDCARD = create("*/*");

    /** 二进制默认类型，与 {@link #APPLICATION_OCTET_STREAM} 同一实例 */
    public static final ContentType DEFAULT_BINARY = APPLICATION_OCTET_STREAM;

    /** 文本默认类型，与 {@link #TEXT_PLAIN} 同一实例 */
    public static final ContentType DEFAULT_TEXT = TEXT_PLAIN;

    private final String mimeType;
    private final Charset charset;

    private ContentType(String mimeType, Charset charset) {
        this.mimeType = mimeType;
        this.charset = charset;
    }

    /**
     * 创建不带字符集的内容类型。
     *
     * @param mimeType MIME 类型，不能为空
     * @throws NullPointerException {@code mimeType} 为 {@code null} 时抛出
     */
    public static ContentType create(String mimeType) {
        return create(mimeType, null);
     }

    /**
     * 创建带字符集的内容类型。
     *
     * @param mimeType MIME 类型，不能为空
     * @param charset  字符集，可为 {@code null}（表示缺省）
     * @throws NullPointerException {@code mimeType} 为 {@code null} 时抛出
     */
    public static ContentType create(String mimeType, Charset charset) {
        Objects.requireNonNull(mimeType, "MIME type");
        return new ContentType(mimeType, charset);
    }

    /**
     * 解析 Content-Type 头部取值为内容类型实例。
     * <p>
     * 取首个逗号分隔的头部元素参与解析：首个分号前为 MIME 类型（去首尾空白，
     * 保留原大小写与内部字符）；其后按分号分隔的参数中，首个名称为
     * {@code charset} 的参数（忽略大小写）作为字符集，取值可成对引号包裹
     * （引号内的分号、逗号与反斜杠转义字符不参与分隔）；该参数取值为空白、
     * 未出现 charset 参数或取值缺省时字符集为 {@code null}，其余参数一律忽略。
     *
     * @param s Content-Type 头部取值
     * @return 内容类型实例；输入或 MIME 类型为空白时返回 {@code null}
     * @throws IllegalCharsetNameException charset 参数取值不是合法字符集名称时抛出
     * @throws UnsupportedCharsetException charset 参数取值合法但当前 JVM 不支持时抛出
     */
    public static ContentType parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        // 头部取值可含多个逗号分隔的元素，仅首个元素参与解析
        int elementEnd = indexOfTopLevel(s, ',', 0);
        String element = elementEnd < 0 ? s : s.substring(0, elementEnd);
        int paramStart = indexOfTopLevel(element, ';', 0);
        String mimeType = (paramStart < 0 ? element : element.substring(0, paramStart)).trim();
        if (mimeType.isEmpty()) {
            return null;
        }
        Charset charset = paramStart < 0 ? null : extractCharset(element, paramStart);
        return create(mimeType, charset);
    }

    /**
     * 在引号外查找目标字符首次出现的位置；引号内的目标字符与
     * 反斜杠转义字符不参与匹配，未命中返回 -1。
     */
    private static int indexOfTopLevel(String s, char target, int from) {
        boolean quoted = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted && c == '\\') {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == target && !quoted) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 逐段扫描分号分隔的参数，返回首个名为 charset 的参数对应的字符集；
     * 扫描在首个 charset 参数处终止，其后同名参数不生效。
     */
    private static Charset extractCharset(String element, int from) {
        int cursor = from;
        while (cursor < element.length()) {
            int next = indexOfTopLevel(element, ';', cursor);
            String segment = next < 0 ? element.substring(cursor) : element.substring(cursor, next);
            cursor = next < 0 ? element.length() : next + 1;
            int equalsIndex = segment.indexOf('=');
            if (equalsIndex < 0 || !"charset".equalsIgnoreCase(segment.substring(0, equalsIndex).trim())) {
                continue;
            }
            String value = unquote(segment.substring(equalsIndex + 1).trim());
            return value.isEmpty() ? null : Charset.forName(value);
        }
        return null;
    }

    /**
     * 去除成对包裹的引号并还原引号内的反斜杠转义字符；无引号包裹时原样返回。
     */
    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length() - 2);
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length() - 1) {
                i++;
                c = value.charAt(i);
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * 获取 MIME 类型（如 {@code application/json}）。
     *
     * @return 创建时给定的 MIME 类型原文
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * 获取字符集。
     *
     * @return 创建时给定的字符集；不带字符集时返回 {@code null}
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 格式化为头部取值：{@code mimeType; charset=NAME}；
     * 无字符集时只输出 {@code mimeType}。
     */
    @Override
    public String toString() {
        return charset == null ? mimeType : mimeType + "; charset=" + charset.name();
    }
}
