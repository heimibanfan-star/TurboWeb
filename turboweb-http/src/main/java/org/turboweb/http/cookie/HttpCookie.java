package org.turboweb.http.cookie;

import org.turboweb.http.response.HttpInfoResponse;

/**
 * 可以设置响应的cookie
 */
public class HttpCookie {

    private final Cookies cookies;
    private final HttpInfoResponse response;

    public HttpCookie(Cookies cookies, HttpInfoResponse response) {
        this.cookies = cookies;
        this.response = response;
    }

    /**
     * 获取cookies
     *
     * @return 所有的cookies
     */
    public Cookies getCookies() {
        return cookies;
    }

    /**
     * 获取cookie
     *
     * @param key cookie的key
     * @return cookie的value
     */
    public String getCookie(String key) {
        return cookies.getCookie(key);
    }

    /**
     * 设置cookie
     *
     * @param key   cookie的key
     * @param value cookie的value
     */
    public void setCookie(String key, String value) {
        response.setCookie(key, value);
    }
}
