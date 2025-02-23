package org.turbo.web.core.http.request;

import java.util.Map;

/**
 * 存储cookie相关信息
 */
public class Cookies {

    private final Map<String, String> cookies;

    public Cookies(Map<String, String> cookies) {
        this.cookies = cookies;
    }

    /**
     * 获取cookie
     *
     * @param key cookie key
     * @return cookie value
     */
    public String getCookie(String key) {
        return cookies.get(key);
    }
}
