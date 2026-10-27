package top.turboweb.http.core.cookie;

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
     * 移除指定 key 的 Cookie（响应阶段设置一个 Max-Age 为 0 的 Cookie）。
     * 实质上并不会影响客户端的 Cookie 状态，除非写入响应头。
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
     * 将当前上下文中的 Cookie 状态序列化为 {@code Set-Cookie} 头值列表，
     * Cookie 值按给定字符集 URL 编码。
     * <p>
     * 先输出本次新增或更新的 Cookie，再输出待删除 Cookie 的过期形态（{@code Max-Age=0}）；
     * 调用后不清空待写与待删集合，重复调用会重复产出。
     *
     * @param charset Cookie 值编码字符集（写出侧以装配期判定的缺省字符集传入），不能为 null
     * @return Set-Cookie 头值列表；无待写与待删 Cookie 时返回空列表
     * @throws NullPointerException charset 为 null
     */
    List<String> toSetCookies(Charset charset);
}

