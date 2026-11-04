package top.turboweb.http.response;

import top.turboweb.http.core.Headers;
import top.turboweb.http.core.StatusLine;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP 响应值对象：承载状态行、响应头与原始业务数据，供结果转化环节装配为响应消息。
 * <p>
 * 本类只做持有，不承载内容编码与实体头的装配：数据按入参原样持有，
 * 序列化与 Content-Type、Content-Length 的补齐由结果转化环节按实际内容完成。
 * 头部为消息自有的 {@link Headers} 实体，读写直接作用于本对象。
 * 提供构造方法和静态工厂方法快速创建不同状态码的响应值对象。
 * </p>
 */
public final class HttpResult {

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";
    /** Content-Disposition 头名 */
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    /** 文件结果缺省内容类型 */
    private static final String DEFAULT_FILE_CONTENT_TYPE = "application/octet-stream";
    /** 百分号编码用的大写十六进制字符表 */
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /** 响应状态行，取自框架状态行注册表 */
    private final StatusLine statusLine;
    /** 响应头实体，构造期一次建立或按入参原样持有 */
    private final Headers headers;
    /** 原始业务数据，可为 null */
    private final Object data;

    public HttpResult(int status) {
        this(status, null, null);
    }

    public HttpResult(Object data) {
        this(200, data);
    }

    public HttpResult(int status, Object data) {
        this(status, null, data);
    }

    public HttpResult(int status, Headers headers, Object data) {
        this.statusLine = StatusLine.of(status);
        this.headers = Objects.requireNonNullElseGet(headers, Headers::new);
        this.data = data;
    }

    /**
     * 获取响应状态行。
     *
     * @return 状态行，已收录状态码为注册表共享实例
     */
    public StatusLine statusLine() {
        return statusLine;
    }

    /**
     * 获取响应头实体。
     *
     * @return 响应头实体，重复访问返回同一实例
     */
    public Headers headers() {
        return headers;
    }

    /**
     * 获取原始业务数据。
     *
     * @return 业务数据，未携带时为 null
     */
    public Object data() {
        return data;
    }

    /**
     * 创建响应值对象
     *
     * @param status  响应状态码
     * @param headers 响应头，为 null 时自建空头部实体
     * @param data    响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult create(int status, Headers headers, Object data) {
        return new HttpResult(status, headers, data);
    }

    /**
     * 创建响应值对象
     *
     * @param status 响应状态码
     * @param data   响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult create(int status, Object data) {
        return new HttpResult(status, data);
    }

    /**
     * 创建响应状态码为200的响应值对象
     *
     * @param data 响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult ok(Object data) {
        return new HttpResult(200, data);
    }

    /**
     * 创建响应状态码为200的响应值对象
     *
     * @return 响应值对象
     */
    public static HttpResult ok() {
        return new HttpResult(200);
    }

    /**
     * 创建响应状态码为500的响应值对象
     *
     * @param data 响应内容，可为 null
     * @return 响应值对象
     */
    public static HttpResult err(Object data) {
        return new HttpResult(500, data);
    }

    /**
     * 创建响应状态码为500的响应值对象
     *
     * @return 响应值对象
     */
    public static HttpResult err() {
        return new HttpResult(500);
    }

    /**
     * 创建状态码为200的文件响应值对象，内容类型为 application/octet-stream，
     * 以附件下载方式写出，文件名取不带连字符的随机 UUID。
     *
     * @param data 文件字节内容，不能为 {@code null}
     * @return 文件响应值对象
     * @throws NullPointerException 当 {@code data} 为 {@code null} 时抛出
     */
    public static HttpResult fileOf(byte[] data) {
        return fileOf(data, DEFAULT_FILE_CONTENT_TYPE);
    }

    /**
     * 创建状态码为200的文件响应值对象，以附件下载方式写出，
     * 文件名取不带连字符的随机 UUID。
     *
     * @param data        文件字节内容，不能为 {@code null}
     * @param contentType 内容类型 MIME 原文，不能为空白
     * @return 文件响应值对象
     * @throws NullPointerException     当 {@code data} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 {@code contentType} 为空白时抛出
     */
    public static HttpResult fileOf(byte[] data, String contentType) {
        return fileOf(data, contentType, false);
    }

    /**
     * 创建状态码为200的文件响应值对象，文件名取不带连字符的随机 UUID。
     *
     * @param data        文件字节内容，不能为 {@code null}
     * @param contentType 内容类型 MIME 原文，不能为空白
     * @param inline      是否内联展开：true 时建议浏览器内联展示，false 时作为附件下载
     * @return 文件响应值对象
     * @throws NullPointerException     当 {@code data} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 {@code contentType} 为空白时抛出
     */
    public static HttpResult fileOf(byte[] data, String contentType, boolean inline) {
        return fileOf(data, contentType, inline, UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * 创建状态码为200的文件响应值对象：文件字节内容按入参原样持有，
     * 内容类型与展示方式、文件名装配为 Content-Type 与 Content-Disposition 头，
     * 文件名含非 ASCII 字符或引号、反斜杠时追加 filename 扩展参数声明编码后的文件名，
     * 内容长度由结果转化环节按实际响应体补齐。
     *
     * @param data        文件字节内容，不能为 {@code null}
     * @param contentType 内容类型 MIME 原文，不能为空白
     * @param inline      是否内联展开：true 时建议浏览器内联展示，false 时作为附件下载
     * @param filename    文件名，不能为空白
     * @return 文件响应值对象
     * @throws NullPointerException     当 {@code data} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 {@code contentType} 或 {@code filename} 为空白时抛出
     */
    public static HttpResult fileOf(byte[] data, String contentType, boolean inline, String filename) {
        Objects.requireNonNull(data, "data must not be null");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        Headers headers = new Headers();
        headers.set(CONTENT_TYPE, contentType);
        headers.set(CONTENT_DISPOSITION, contentDispositionOf(inline, filename));
        return new HttpResult(200, headers, data);
    }

    /**
     * 装配 Content-Disposition 头取值：展示方式与文件名装配为 filename 基本参数，
     * 文件名按引号串转义规则转义后置于引号串内，含非 ASCII 字符或引号、反斜杠时
     * 按 RFC 5987 追加 filename 扩展参数，以 UTF-8 百分号编码形式声明文件名，
     * 供浏览器优先取用。
     *
     * @param inline   是否内联展开：true 时建议浏览器内联展示，false 时作为附件下载
     * @param filename 文件名，已由调用方保证非空白
     * @return Content-Disposition 头取值
     */
    private static String contentDispositionOf(boolean inline, String filename) {
        String value = (inline ? "inline" : "attachment") + "; filename=\"" + escapeQuotedString(filename) + "\"";
        if (!isAscii(filename) || filename.indexOf('"') >= 0 || filename.indexOf('\\') >= 0) {
            return value + "; filename*=UTF-8''" + encodeRfc5987(filename);
        }
        return value;
    }

    /**
     * 按引号串转义规则转义文件名：反斜杠与双引号前补反斜杠，
     * 保证文件名可原义置于 filename 参数的引号串内。
     *
     * @param filename 文件名
     * @return 转义后的引号串取值，不含需转义字符时原样返回
     */
    private static String escapeQuotedString(String filename) {
        if (filename.indexOf('\\') < 0 && filename.indexOf('"') < 0) {
            return filename;
        }
        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 判断文件名是否全为 ASCII 字符。
     *
     * @param filename 文件名
     * @return 全为 ASCII 字符返回 true
     */
    private static boolean isAscii(String filename) {
        for (int i = 0; i < filename.length(); i++) {
            if (filename.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按 RFC 5987 将文件名编码为扩展参数取值：非保留字符原样输出，
     * 其余字符按 UTF-8 字节流逐字节百分号编码。
     *
     * @param filename 文件名
     * @return 百分号编码后的扩展参数取值
     */
    private static String encodeRfc5987(String filename) {
        byte[] bytes = filename.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                encoded.append(c);
            } else {
                encoded.append('%').append(HEX_DIGITS[(c >> 4) & 0xF]).append(HEX_DIGITS[c & 0xF]);
            }
        }
        return encoded.toString();
    }
}
