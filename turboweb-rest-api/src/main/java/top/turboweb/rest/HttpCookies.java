package top.turboweb.rest;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 请求级 Cookie 集合接口，统一管理当前请求过程中的 Cookie 数据。
 * 该接口既支持读取客户端发送的 Cookie，也支持设置服务端响应的 Set-Cookie。
 * 适用于框架内部中间件、控制器或过滤器中对 Cookie 的统一访问和写入。
 */
public interface HttpCookies {

    /**
     * 获取指定 key 对应的 Cookie 对象（包含完整属性）。
     * 通常用于访问客户端随请求发送的 Cookie 或框架中已经设置的 Cookie。
     *
     * @param key Cookie 的名称（不区分大小写）
     * @return Cookie 对象，若不存在返回 null
     */
    String getCookie(String key);

    /**
     * 设置一个完整的 Cookie 实例（用于响应写入）。
     * 若已存在同名 Cookie，将会覆盖。
     *
     * @param cookie 要设置的 Cookie 对象，不能为空
     */
    void setCookie(HttpCookie cookie);

    /**
     * 快捷方式：设置一个 key-value 的 Cookie（不包含其它属性）。
     * 默认 path 为 "/"，maxAge 为 -1（会话 Cookie）。
     *
     * @param key Cookie 名称
     * @param value Cookie 值
     */
    void setCookie(String key, String value);

    /**
     * 快捷设置 Cookie 的同时允许对 Cookie 对象进行配置（例如设置 path、httpOnly 等）。
     * 常用于业务中快速设置带属性的 Cookie。
     *
     * @param key Cookie 名称
     * @param value Cookie 值
     * @param consumer 用于配置 Cookie 属性的回调函数（如设置 path、maxAge 等）
     */
    void setCookie(String key, String value, Consumer<HttpCookie> consumer);

    /**
     * 获取当前请求中所有 Cookie 的键值对集合，仅包含名称和值。
     * 不包含 path、domain、httpOnly 等附加属性。
     *
     * @return 所有 Cookie 的键值映射
     */
    Map<String, String> getCookies();

    /**
     * 移除指定 key 的 Cookie：本轮新设置的直接被撤销，其余登记为待删除，
     * 经 {@link #toSetCookies(Charset)} 写出 {@code Max-Age} 为 0 的过期取值。
     * 移除只作用于本集合的状态，客户端侧要等该取值随响应写出后才失效。
     *
     * @param key 要移除的 Cookie 名称
     */
    void removeCookie(String key);

    /**
     * 清除当前上下文中所有待写入的 Cookie。
     * 常用于中间件重置 Cookie 状态或请求结束前的清理操作。
     */
    void clearToWriteCookies();

    /**
     * 清空当前上下文中所有 Cookie。
     * 常用于中间件或控制器中清除 Cookie。
     */
    void clearAll();

    /**
     * 收集本轮待写入与待删除的 Cookie，各自序列化为一条 {@code Set-Cookie} 头取值：
     * 本轮设置过的 Cookie 按当前属性写出，待删除的 Cookie 写出 {@code Max-Age} 为 0 的过期取值。
     * <p>待写入的取值在前、待删除的在后，同一组内顺序不保证。调用后不自动清空待写入集合，
     * 应在适当时机调用 {@link #clearToWriteCookies()}。
     *
     * @param charset Cookie 值 URL 编码字符集（写出侧以装配期判定的缺省字符集传入），不能为 null
     * @return {@code Set-Cookie} 头取值列表；无待写入与待删除 Cookie 时返回空列表
     * @throws NullPointerException charset 为 null
     */
    List<String> toSetCookies(Charset charset);
}
