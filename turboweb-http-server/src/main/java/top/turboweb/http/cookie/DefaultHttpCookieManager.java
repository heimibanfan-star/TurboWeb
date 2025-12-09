package top.turboweb.http.cookie;


import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 默认的 {@link HttpCookieManager} 实现。
 * <p>
 * 该类负责解析请求头中的 Cookie 信息，并在响应阶段统一管理新增与删除的 Cookie。
 * 它在请求到达时从 {@link HttpHeaders} 中解析 {@code Cookie} 字段，
 * 并维护三类数据结构：
 * <ul>
 *     <li>{@code httpCookieMap}：当前请求中的所有 Cookie 实例；</li>
 *     <li>{@code toWriteHttpCookies}：本次请求需要写入响应的 Cookie；</li>
 *     <li>{@code toRemoveHttpCookies}：本次请求需要删除的 Cookie。</li>
 * </ul>
 * <p>
 * 在响应阶段通过 {@link #setCookieForResponse(HttpResponse)} 方法，
 * 自动将新增和删除的 Cookie 以 {@code Set-Cookie} 头写入响应。
 *
 * <p><b>线程安全性：</b>本类实例与请求绑定，不应在多线程间共享。</p>
 */
public class DefaultHttpCookieManager implements HttpCookieManager {

    private final Map<String, HttpCookie> httpCookieMap;
    private final Set<String> toWriteHttpCookies = new HashSet<>();
    private final Set<String> toRemoveHttpCookies = new HashSet<>();

    /**
     * 基于请求头构造默认 Cookie 管理器。
     * <p>
     * 会从请求头中解析 {@code Cookie} 字段并填充内部 {@link #httpCookieMap}。
     * 当请求中不存在 Cookie 时，将创建空的 Cookie 映射。
     *
     * @param requestHeaders 当前请求的 HTTP 头信息
     */
    public DefaultHttpCookieManager(HttpHeaders requestHeaders) {
        String cookie = requestHeaders.get(HttpHeaderNames.COOKIE);
        if (cookie != null) {
            String[] cookieArray = cookie.split(";");
            httpCookieMap = new HashMap<>(cookieArray.length + 8, 1);
            for (String s : cookieArray) {
                int index = s.indexOf('=');
                if (index > 0) {
                    String key = s.substring(0, index).trim();
                    String value = s.substring(index + 1).trim();
                    httpCookieMap.put(key, new HttpCookie(key, value));
                }
            }
        } else {
            httpCookieMap = new HashMap<>(8, 1);
        }

    }

    /**
     * 获取指定名称的 Cookie 值。
     *
     * @param key Cookie 名称
     * @return 对应的 Cookie 值，若不存在则返回 {@code null}
     */
    @Override
    public String getCookie(String key) {
        HttpCookie cookie = httpCookieMap.get(key);
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
    }

    /**
     * 设置或更新 Cookie。
     * <p>
     * 若同名 Cookie 已存在，则会被覆盖。
     *
     * @param cookie 要设置的 {@link HttpCookie} 对象
     */
    @Override
    public void setCookie(HttpCookie cookie) {
        httpCookieMap.put(cookie.getKey(), cookie);
        toWriteHttpCookies.add(cookie.getKey());
    }

    /**
     * 设置或更新 Cookie。
     *
     * @param key   Cookie 名称
     * @param value Cookie 值
     */
    @Override
    public void setCookie(String key, String value) {
        setCookie(new HttpCookie(key, value));
    }

    /**
     * 设置 Cookie，并允许调用方通过 {@link Consumer} 自定义属性。
     * <p>
     * 典型用法：
     * <pre>{@code
     * cookieManager.setCookie("token", "abc123", c -> {
     *     c.setHttpOnly(true);
     *     c.setMaxAge(3600);
     * });
     * }</pre>
     *
     * @param key       Cookie 名称
     * @param value     Cookie 值
     * @param consumer  自定义 Cookie 属性的回调函数
     */
    @Override
    public void setCookie(String key, String value, Consumer<HttpCookie> consumer) {
        HttpCookie cookie = new HttpCookie(key, value);
        consumer.accept(cookie);
        setCookie(cookie);
    }

    /**
     * 获取当前所有 Cookie 的键值对。
     *
     * @return Cookie 名称与值的映射表（副本）
     */
    @Override
    public Map<String, String> getCookies() {
        return httpCookieMap.entrySet()
                .stream()
                .collect(
                        HashMap::new,
                        (map, entry) ->
                                map.put(entry.getKey(), entry.getValue().getValue()),
                        HashMap::putAll
                );
    }

    /**
     * 删除指定名称的 Cookie。
     * <p>
     * 若 Cookie 属于新写入列表（即本次请求中新增），
     * 则直接从新增集合中移除；
     * 否则会标记为待删除，并在响应中设置 {@code Max-Age=0}。
     *
     * @param key 要删除的 Cookie 名称
     */
    @Override
    public void removeCookie(String key) {
        HttpCookie cookie = httpCookieMap.get(key);
        if (cookie == null) {
            return;
        }
        // 判断是否是待写入的cookie
        if (toWriteHttpCookies.contains(key)) {
            httpCookieMap.remove(key);
            toWriteHttpCookies.remove(key);
        } else {
            httpCookieMap.remove(key);
            toRemoveHttpCookies.add(key);
        }
    }

    /**
     * 清空所有待写入响应的新 Cookie。
     * <p>
     * 通常在手动构建响应后调用，以避免重复写入。
     */
    @Override
    public void clearToWriteCookies() {
        toWriteHttpCookies.clear();
    }

    /**
     * 清空当前 Cookie 状态。
     * <p>
     * 会移除所有新建的 Cookie，并将剩余 Cookie 全部标记为待删除。
     * 适用于用户登出或会话终止等场景。
     */
    @Override
    public void clearAll() {
        toRemoveHttpCookies.clear();
        for (String key : toWriteHttpCookies) {
            httpCookieMap.remove(key);
        }
        toWriteHttpCookies.clear();
        toRemoveHttpCookies.addAll(httpCookieMap.keySet());
    }

    /**
     * 将 Cookie 写入响应。
     * <p>
     * 该方法会将：
     * <ul>
     *     <li>新增的 Cookie（{@code toWriteHttpCookies}）以 {@code Set-Cookie} 头形式添加；</li>
     *     <li>待删除的 Cookie（{@code toRemoveHttpCookies}）以 {@code Max-Age=0} 形式添加。</li>
     * </ul>
     * 调用后不自动清空集合，应在适当时机调用 {@link #clearToWriteCookies()}。
     *
     * @param response 当前响应对象
     */
    @Override
    public void setCookieForResponse(HttpResponse response) {
        // 判断是否有要新写入的cookie
        if (!toWriteHttpCookies.isEmpty()) {
            for (String key : toWriteHttpCookies) {
                HttpCookie cookie = httpCookieMap.get(key);
                if (cookie == null) {
                    continue;
                }
                response.headers().add(HttpHeaderNames.SET_COOKIE, cookie.toHeaderString());
            }
        }
        // 判断是否有待删除的Cookie
        if (!toRemoveHttpCookies.isEmpty()) {
            for (String key : toRemoveHttpCookies) {
                HttpCookie cookie = new HttpCookie(key, "");
                cookie.setMaxAge(0);
                response.headers().add(HttpHeaderNames.SET_COOKIE, cookie.toHeaderString());
            }
        }
    }
}
