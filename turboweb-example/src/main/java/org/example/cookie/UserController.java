package org.example.cookie;


import top.turboweb.anno.method.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookie;
import top.turboweb.http.cookie.HttpCookieManager;

@RequestPath("/user")
public class UserController {

    @Get("/set")
    public String setCookie(HttpContext context) {
        context.cookie().setCookie("name", "turbo");
        return "setCookie";
    }


    @Get("/get")
    public String getCookie(HttpContext context) {
        String name = context.cookie().getCookie("name");
        return "name: " + name;
    }


    @Get("/rem")
    public String removeCookie(HttpContext context) {
        context.cookie().removeCookie("name");
        return "remCookie";
    }


    @Get("/set2")
    public String setCookieWithAttributes(HttpContext context) {
        HttpCookie cookie = new HttpCookie("name", "turbo");
        cookie.setMaxAge(3600);        // 1小时后过期
        cookie.setPath("/user");       // 限定路径
        context.cookie().setCookie(cookie);
        return "setCookie2";
    }


    @Get("/clear")
    public String clearToWriteCookies(HttpContext context) {
        HttpCookieManager cookieManager = context.cookie();
        cookieManager.setCookie("a", "1");
        cookieManager.setCookie("b", "2");
        cookieManager.setCookie("c", "3");
        cookieManager.clearToWriteCookies();
        return "clearCookie";
    }


    @Get("/clearAll")
    public String clearAllCookies(HttpContext context) {
        context.cookie().clearAll();
        return "clearAllCookie";
    }

}
