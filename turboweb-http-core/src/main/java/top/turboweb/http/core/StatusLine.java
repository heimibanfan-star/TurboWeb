package top.turboweb.http.core;

/**
 * http状态行：存储响应状态码，对应状态行的状态码字段。
 *
 * @param code 响应状态码
 */
public record StatusLine(int code) {
}
