package top.turboweb.http.cookie;


import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultHttpCookieManager implements HttpCookieManager {

    private final Map<String, HttpCookie> httpCookieMap;
    private final Set<String> toWriteHttpCookies = new HashSet<>();
    private final Set<String> toRemoveHttpCookies = new HashSet<>();

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

    @Override
    public String getCookie(String key) {
        HttpCookie cookie = httpCookieMap.get(key);
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
    }

    @Override
    public void setCookie(HttpCookie cookie) {
        httpCookieMap.put(cookie.getKey(), cookie);
        toWriteHttpCookies.add(cookie.getKey());
    }

    @Override
    public void setCookie(String key, String value) {
        setCookie(new HttpCookie(key, value));
    }

    @Override
    public void setCookie(String key, String value, Consumer<HttpCookie> consumer) {
        HttpCookie cookie = new HttpCookie(key, value);
        consumer.accept(cookie);
        setCookie(cookie);
    }

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

    @Override
    public void clearToWriteCookies() {
        toWriteHttpCookies.clear();
    }

    @Override
    public void clearAll() {
        toRemoveHttpCookies.clear();
        for (String key : toWriteHttpCookies) {
            httpCookieMap.remove(key);
        }
        toWriteHttpCookies.clear();
        toRemoveHttpCookies.addAll(httpCookieMap.keySet());
    }

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
