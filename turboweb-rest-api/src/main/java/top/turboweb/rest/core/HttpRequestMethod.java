package top.turboweb.rest.core;

/**
 * HTTP 请求方式枚举：标注请求行方法字段对应的标准方法，覆盖 RFC 9110 定义的
 * {@code GET}、{@code HEAD}、{@code POST}、{@code PUT}、{@code DELETE}、
 * {@code CONNECT}、{@code OPTIONS}、{@code TRACE} 与 RFC 5789 的 {@code PATCH}。
 *
 * <p>枚举常量名即协议线上的方法名，请求行取到的方法字面量直接经
 * {@link #valueOf(String)} 转化为枚举：方法名大小写敏感（与协议一致），
 * 字面量不在枚举范围内（扩展方法）时抛出 {@link IllegalArgumentException}。
 * 请求行方法字段的原文仍经消息的 {@code method()} 读取，自定义扩展方法不经本枚举表达。</p>
 */
public enum HttpRequestMethod {

    /** 获取资源，只读且幂等 */
    GET,

    /** 只获取响应头，不返回响应体 */
    HEAD,

    /** 提交数据，由服务端决定资源归属 */
    POST,

    /** 以请求体整体替换目标资源，幂等 */
    PUT,

    /** 对资源做局部更新 */
    PATCH,

    /** 删除目标资源，幂等 */
    DELETE,

    /** 查询目标资源的选项与允许的方法 */
    OPTIONS,

    /** 将请求内容原样回送，用于消息链路诊断 */
    TRACE,

    /** 在通道上建立协议级桥接 */
    CONNECT;
}
