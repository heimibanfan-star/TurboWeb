package top.turboweb.rest;

import java.nio.charset.Charset;

/**
 * {@code HttpCookie} 表示一个 HTTP Cookie 的属性契约：名称、值，以及路径、域、
 * 最大存活时间、HttpOnly 四项可选属性。
 * <p>
 * 属性可读可写；路径与域写入 {@code null} 或空串时保持原值，即缺省形态不可被空值覆盖。
 * <p>
 * 本契约不含 Secure 属性，与框架当前不支持 HTTPS 的场景对应。
 */
public interface HttpCookie {

    /**
     * 获取 Cookie 名称。
     *
     * @return Cookie 名称，非空
     */
    String getKey();

    /**
     * 获取 Cookie 值。
     *
     * @return Cookie 值，非空（可为空串）
     */
    String getValue();

    /**
     * 设置 Cookie 作用的路径范围。浏览器仅在请求路径匹配该路径时发送本 Cookie；
     * 缺省为 {@code /}，即根路径及其所有子路径。
     *
     * @param path 路径；为 {@code null} 或空串时保持原值
     */
    void setPath(String path);

    /**
     * 设置 Cookie 作用的域名，可设为子域通配（如 {@code .example.com}）。
     * 缺省不写出该属性，即作用于当前域名。
     *
     * @param domain 域名；为 {@code null} 或空串时保持原值
     */
    void setDomain(String domain);

    /**
     * 设置最大存活秒数：非负值为自设置时刻起的存活秒数，{@code 0} 表示立即过期；
     * 负值（缺省 {@code -1}）表示会话 Cookie，浏览器关闭后失效。
     *
     * @param maxAge 最大存活秒数
     */
    void setMaxAge(long maxAge);

    /**
     * 设置是否带 HttpOnly 属性；置位后客户端脚本无法读取本 Cookie。
     * 缺省不带该属性。
     *
     * @param httpOnly 是否带 HttpOnly 属性
     */
    void setHttpOnly(boolean httpOnly);

    /**
     * 序列化本 Cookie 为 {@code Set-Cookie} 头的取值：先写 {@code 名称=值}，
     * 再按 Path、Domain、Max-Age、HttpOnly 的顺序追加已设置的属性，属性间以 {@code "; "} 分隔；
     * 域未设置、最大存活时间为负、HttpOnly 为 false 时对应属性整段省略。
     * <p>值按给定字符集做 URL 编码，防止特殊字符破坏头部形态。
     *
     * @param charset Cookie 值 URL 编码所用字符集，不能为 {@code null}
     * @return {@code Set-Cookie} 头取值
     * @throws NullPointerException {@code charset} 为 {@code null} 时抛出
     */
    String toSetCookie(Charset charset);
}
