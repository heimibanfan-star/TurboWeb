package top.turboweb.http.cookie;

import java.nio.charset.Charset;

/**
 * 表示一个 HTTP Cookie 的契约，封装 Cookie 的名称、值与可选属性。
 * <p>
 * 实现类负责自身的构造校验，并将自身序列化为 {@code Set-Cookie} 头值；
 * 序列化产物由调用方写入响应头，本接口不依赖响应对象。
 */
public interface HttpCookie {

    /**
     * 获取 Cookie 的名称。
     *
     * @return Cookie 名称
     */
    String getKey();

    /**
     * 获取 Cookie 的值。
     *
     * @return Cookie 值
     */
    String getValue();

    /**
     * 设置 Cookie 的路径属性。
     *
     * @param path 路径；为 {@code null} 或空串时实现类保留原有取值
     */
    void setPath(String path);

    /**
     * 设置 Cookie 的域属性。
     *
     * @param domain 域名；为 {@code null} 或空串时实现类保留原有取值
     */
    void setDomain(String domain);

    /**
     * 设置 Cookie 的最大存活时间（单位：秒）。
     *
     * @param maxAge 最大存活时间；负数表示会话 Cookie，{@code 0} 表示立即过期
     */
    void setMaxAge(long maxAge);

    /**
     * 设置是否携带 HttpOnly 属性。
     *
     * @param httpOnly 是否禁止客户端脚本访问该 Cookie
     */
    void setHttpOnly(boolean httpOnly);

    /**
     * 转换为符合 {@code Set-Cookie} 头的字符串表示。
     * 会对 Cookie 值按给定字符集进行 URL 编码，防止特殊字符导致解析错误。
     *
     * @param charset Cookie 值 URL 编码字符集（写出侧以装配期判定的缺省字符集传入），不能为 null
     * @return Set-Cookie 头字符串
     * @throws NullPointerException charset 为 null
     */
    String toSetCookie(Charset charset);
}
