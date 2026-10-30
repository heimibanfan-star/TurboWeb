package top.turboweb.http.core;

import java.util.Objects;

/**
 * http 状态行：承接响应的状态码与状态描述短语。
 *
 * <p>标准状态码以常量提供共享实例，并经静态注册表按状态码索引。实例只能经
 * {@link #of(int)} 与 {@link #of(int, String)} 创建：已收录的状态码一律返回注册表
 * 中的共享实例；未收录的状态码返回新实例，单参形式的描述短语为
 * {@code Unknown Status(code)}，双参形式采用指定的描述短语。</p>
 */
public final class StatusLine {

    /** 响应状态码，非负整数 */
    private final int code;

    /** 状态描述短语 */
    private final String reason;

    /**
     * 建立状态行。
     *
     * @param code   响应状态码，非负整数
     * @param reason 状态描述短语，不能为空，且不得含回车或换行
     * @throws IllegalArgumentException 当 {@code code} 为负数，或 {@code reason}
     *                                  含回车或换行时抛出
     * @throws NullPointerException     当 {@code reason} 为 {@code null} 时抛出
     */
    private StatusLine(int code, String reason) {
        if (code < 0) {
            throw new IllegalArgumentException("code must be positive or zero");
        }
        for (int i = 0; i < reason.length(); i++) {
            char c = reason.charAt(i);
            if (c == '\n' || c == '\r') {
                throw new IllegalArgumentException(
                        "reason contains one of the following prohibited characters: \\r\\n: " + reason);
            }
        }
        this.code = code;
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * 100 Continue。
     */
    public static final StatusLine CONTINUE = new StatusLine(100, "Continue");

    /**
     * 101 Switching Protocols。
     */
    public static final StatusLine SWITCHING_PROTOCOLS = new StatusLine(101, "Switching Protocols");

    /**
     * 102 Processing。
     */
    public static final StatusLine PROCESSING = new StatusLine(102, "Processing");

    /**
     * 103 Early Hints。
     */
    public static final StatusLine EARLY_HINTS = new StatusLine(103, "Early Hints");

    /**
     * 200 OK。
     */
    public static final StatusLine OK = new StatusLine(200, "OK");

    /**
     * 201 Created。
     */
    public static final StatusLine CREATED = new StatusLine(201, "Created");

    /**
     * 202 Accepted。
     */
    public static final StatusLine ACCEPTED = new StatusLine(202, "Accepted");

    /**
     * 203 Non-Authoritative Information。
     */
    public static final StatusLine NON_AUTHORITATIVE_INFORMATION = new StatusLine(203, "Non-Authoritative Information");

    /**
     * 204 No Content。
     */
    public static final StatusLine NO_CONTENT = new StatusLine(204, "No Content");

    /**
     * 205 Reset Content。
     */
    public static final StatusLine RESET_CONTENT = new StatusLine(205, "Reset Content");

    /**
     * 206 Partial Content。
     */
    public static final StatusLine PARTIAL_CONTENT = new StatusLine(206, "Partial Content");

    /**
     * 207 Multi-Status。
     */
    public static final StatusLine MULTI_STATUS = new StatusLine(207, "Multi-Status");

    /**
     * 208 Already Reported。
     */
    public static final StatusLine ALREADY_REPORTED = new StatusLine(208, "Already Reported");

    /**
     * 226 IM Used。
     */
    public static final StatusLine IM_USED = new StatusLine(226, "IM Used");

    /**
     * 300 Multiple Choices。
     */
    public static final StatusLine MULTIPLE_CHOICES = new StatusLine(300, "Multiple Choices");

    /**
     * 301 Moved Permanently。
     */
    public static final StatusLine MOVED_PERMANENTLY = new StatusLine(301, "Moved Permanently");

    /**
     * 302 Found。
     */
    public static final StatusLine FOUND = new StatusLine(302, "Found");

    /**
     * 303 See Other。
     */
    public static final StatusLine SEE_OTHER = new StatusLine(303, "See Other");

    /**
     * 304 Not Modified。
     */
    public static final StatusLine NOT_MODIFIED = new StatusLine(304, "Not Modified");

    /**
     * 305 Use Proxy。
     */
    public static final StatusLine USE_PROXY = new StatusLine(305, "Use Proxy");

    /**
     * 307 Temporary Redirect。
     */
    public static final StatusLine TEMPORARY_REDIRECT = new StatusLine(307, "Temporary Redirect");

    /**
     * 308 Permanent Redirect。
     */
    public static final StatusLine PERMANENT_REDIRECT = new StatusLine(308, "Permanent Redirect");

    /**
     * 400 Bad Request。
     */
    public static final StatusLine BAD_REQUEST = new StatusLine(400, "Bad Request");

    /**
     * 401 Unauthorized。
     */
    public static final StatusLine UNAUTHORIZED = new StatusLine(401, "Unauthorized");

    /**
     * 402 Payment Required。
     */
    public static final StatusLine PAYMENT_REQUIRED = new StatusLine(402, "Payment Required");

    /**
     * 403 Forbidden。
     */
    public static final StatusLine FORBIDDEN = new StatusLine(403, "Forbidden");

    /**
     * 404 Not Found。
     */
    public static final StatusLine NOT_FOUND = new StatusLine(404, "Not Found");

    /**
     * 405 Method Not Allowed。
     */
    public static final StatusLine METHOD_NOT_ALLOWED = new StatusLine(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable。
     */
    public static final StatusLine NOT_ACCEPTABLE = new StatusLine(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required。
     */
    public static final StatusLine PROXY_AUTHENTICATION_REQUIRED = new StatusLine(407, "Proxy Authentication Required");

    /**
     * 408 Request Timeout。
     */
    public static final StatusLine REQUEST_TIMEOUT = new StatusLine(408, "Request Timeout");

    /**
     * 409 Conflict。
     */
    public static final StatusLine CONFLICT = new StatusLine(409, "Conflict");

    /**
     * 410 Gone。
     */
    public static final StatusLine GONE = new StatusLine(410, "Gone");

    /**
     * 411 Length Required。
     */
    public static final StatusLine LENGTH_REQUIRED = new StatusLine(411, "Length Required");

    /**
     * 412 Precondition Failed。
     */
    public static final StatusLine PRECONDITION_FAILED = new StatusLine(412, "Precondition Failed");

    /**
     * 413 Content Too Large。
     */
    public static final StatusLine CONTENT_TOO_LARGE = new StatusLine(413, "Content Too Large");

    /**
     * 414 URI Too Long。
     */
    public static final StatusLine URI_TOO_LONG = new StatusLine(414, "URI Too Long");

    /**
     * 415 Unsupported Media Type。
     */
    public static final StatusLine UNSUPPORTED_MEDIA_TYPE = new StatusLine(415, "Unsupported Media Type");

    /**
     * 416 Range Not Satisfiable。
     */
    public static final StatusLine RANGE_NOT_SATISFIABLE = new StatusLine(416, "Range Not Satisfiable");

    /**
     * 417 Expectation Failed。
     */
    public static final StatusLine EXPECTATION_FAILED = new StatusLine(417, "Expectation Failed");

    /**
     * 421 Misdirected Request。
     */
    public static final StatusLine MISDIRECTED_REQUEST = new StatusLine(421, "Misdirected Request");

    /**
     * 422 Unprocessable Content。
     */
    public static final StatusLine UNPROCESSABLE_CONTENT = new StatusLine(422, "Unprocessable Content");

    /**
     * 423 Locked。
     */
    public static final StatusLine LOCKED = new StatusLine(423, "Locked");

    /**
     * 424 Failed Dependency。
     */
    public static final StatusLine FAILED_DEPENDENCY = new StatusLine(424, "Failed Dependency");

    /**
     * 425 Too Early。
     */
    public static final StatusLine TOO_EARLY = new StatusLine(425, "Too Early");

    /**
     * 426 Upgrade Required。
     */
    public static final StatusLine UPGRADE_REQUIRED = new StatusLine(426, "Upgrade Required");

    /**
     * 428 Precondition Required。
     */
    public static final StatusLine PRECONDITION_REQUIRED = new StatusLine(428, "Precondition Required");

    /**
     * 429 Too Many Requests。
     */
    public static final StatusLine TOO_MANY_REQUESTS = new StatusLine(429, "Too Many Requests");

    /**
     * 431 Request Header Fields Too Large。
     */
    public static final StatusLine REQUEST_HEADER_FIELDS_TOO_LARGE = new StatusLine(431, "Request Header Fields Too Large");

    /**
     * 451 Unavailable For Legal Reasons。
     */
    public static final StatusLine UNAVAILABLE_FOR_LEGAL_REASONS = new StatusLine(451, "Unavailable For Legal Reasons");

    /**
     * 500 Internal Server Error。
     */
    public static final StatusLine INTERNAL_SERVER_ERROR = new StatusLine(500, "Internal Server Error");

    /**
     * 501 Not Implemented。
     */
    public static final StatusLine NOT_IMPLEMENTED = new StatusLine(501, "Not Implemented");

    /**
     * 502 Bad Gateway。
     */
    public static final StatusLine BAD_GATEWAY = new StatusLine(502, "Bad Gateway");

    /**
     * 503 Service Unavailable。
     */
    public static final StatusLine SERVICE_UNAVAILABLE = new StatusLine(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout。
     */
    public static final StatusLine GATEWAY_TIMEOUT = new StatusLine(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported。
     */
    public static final StatusLine HTTP_VERSION_NOT_SUPPORTED = new StatusLine(505, "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates。
     */
    public static final StatusLine VARIANT_ALSO_NEGOTIATES = new StatusLine(506, "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage。
     */
    public static final StatusLine INSUFFICIENT_STORAGE = new StatusLine(507, "Insufficient Storage");

    /**
     * 508 Loop Detected。
     */
    public static final StatusLine LOOP_DETECTED = new StatusLine(508, "Loop Detected");

    /**
     * 510 Not Extended。
     */
    public static final StatusLine NOT_EXTENDED = new StatusLine(510, "Not Extended");

    /**
     * 511 Network Authentication Required。
     */
    public static final StatusLine NETWORK_AUTHENTICATION_REQUIRED = new StatusLine(511, "Network Authentication Required");

    /** 标准状态码注册表：下标为状态码，值为携带标准短语的共享实例，容量覆盖全部三位状态码 */
    private static final StatusLine[] CODES;

    static {
        StatusLine[] codes = new StatusLine[1000];
        codes[100] = CONTINUE;
        codes[101] = SWITCHING_PROTOCOLS;
        codes[102] = PROCESSING;
        codes[103] = EARLY_HINTS;
        codes[200] = OK;
        codes[201] = CREATED;
        codes[202] = ACCEPTED;
        codes[203] = NON_AUTHORITATIVE_INFORMATION;
        codes[204] = NO_CONTENT;
        codes[205] = RESET_CONTENT;
        codes[206] = PARTIAL_CONTENT;
        codes[207] = MULTI_STATUS;
        codes[208] = ALREADY_REPORTED;
        codes[226] = IM_USED;
        codes[300] = MULTIPLE_CHOICES;
        codes[301] = MOVED_PERMANENTLY;
        codes[302] = FOUND;
        codes[303] = SEE_OTHER;
        codes[304] = NOT_MODIFIED;
        codes[305] = USE_PROXY;
        codes[307] = TEMPORARY_REDIRECT;
        codes[308] = PERMANENT_REDIRECT;
        codes[400] = BAD_REQUEST;
        codes[401] = UNAUTHORIZED;
        codes[402] = PAYMENT_REQUIRED;
        codes[403] = FORBIDDEN;
        codes[404] = NOT_FOUND;
        codes[405] = METHOD_NOT_ALLOWED;
        codes[406] = NOT_ACCEPTABLE;
        codes[407] = PROXY_AUTHENTICATION_REQUIRED;
        codes[408] = REQUEST_TIMEOUT;
        codes[409] = CONFLICT;
        codes[410] = GONE;
        codes[411] = LENGTH_REQUIRED;
        codes[412] = PRECONDITION_FAILED;
        codes[413] = CONTENT_TOO_LARGE;
        codes[414] = URI_TOO_LONG;
        codes[415] = UNSUPPORTED_MEDIA_TYPE;
        codes[416] = RANGE_NOT_SATISFIABLE;
        codes[417] = EXPECTATION_FAILED;
        codes[421] = MISDIRECTED_REQUEST;
        codes[422] = UNPROCESSABLE_CONTENT;
        codes[423] = LOCKED;
        codes[424] = FAILED_DEPENDENCY;
        codes[425] = TOO_EARLY;
        codes[426] = UPGRADE_REQUIRED;
        codes[428] = PRECONDITION_REQUIRED;
        codes[429] = TOO_MANY_REQUESTS;
        codes[431] = REQUEST_HEADER_FIELDS_TOO_LARGE;
        codes[451] = UNAVAILABLE_FOR_LEGAL_REASONS;
        codes[500] = INTERNAL_SERVER_ERROR;
        codes[501] = NOT_IMPLEMENTED;
        codes[502] = BAD_GATEWAY;
        codes[503] = SERVICE_UNAVAILABLE;
        codes[504] = GATEWAY_TIMEOUT;
        codes[505] = HTTP_VERSION_NOT_SUPPORTED;
        codes[506] = VARIANT_ALSO_NEGOTIATES;
        codes[507] = INSUFFICIENT_STORAGE;
        codes[508] = LOOP_DETECTED;
        codes[510] = NOT_EXTENDED;
        codes[511] = NETWORK_AUTHENTICATION_REQUIRED;
        CODES = codes;
    }

    /**
     * 按状态码查询状态行。
     *
     * @param code 响应状态码，非负整数
     * @return 已收录的状态码返回注册表中的共享实例；未收录的状态码返回描述短语为
     * {@code Unknown Status(code)} 的新实例
     * @throws IllegalArgumentException 当 {@code code} 为负数时抛出
     */
    public static StatusLine of(int code) {
        if (code < 0) {
            throw new IllegalArgumentException("code must be positive or zero");
        }
        StatusLine statusLine = code < CODES.length ? CODES[code] : null;
        return statusLine == null ? new StatusLine(code, "Unknown Status(" + code + ")") : statusLine;
    }

    /**
     * 按状态码与描述短语查询状态行。
     *
     * @param code   响应状态码，非负整数
     * @param reason 状态描述短语，仅对未收录的状态码生效，不能为空，且不得含回车或换行
     * @return 已收录的状态码返回注册表中的共享实例；未收录的状态码返回以指定
     * 描述短语建立的新实例
     * @throws IllegalArgumentException 当 {@code code} 为负数，或 {@code reason}
     *                                  含回车或换行时抛出
     * @throws NullPointerException     当 {@code reason} 为 {@code null} 时抛出
     */
    public static StatusLine of(int code, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        StatusLine statusLine = code >= 0 && code < CODES.length ? CODES[code] : null;
        return statusLine == null ? new StatusLine(code, reason) : statusLine;
    }

    /**
     * 获取响应状态码。
     *
     * @return 响应状态码
     */
    public int code() {
        return code;
    }

    /**
     * 获取状态描述短语。
     *
     * @return 状态描述短语
     */
    public String reason() {
        return reason;
    }

    /**
     * 判等依据状态码与状态描述短语。
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StatusLine other)) {
            return false;
        }
        return code == other.code && reason.equals(other.reason);
    }

    /**
     * 哈希与判等口径一致，依据状态码与状态描述短语。
     */
    @Override
    public int hashCode() {
        return Objects.hash(code, reason);
    }

    /**
     * 以状态码与描述短语拼组呈现，形如 {@code 200 OK}。
     */
    @Override
    public String toString() {
        return code + " " + reason;
    }
}
