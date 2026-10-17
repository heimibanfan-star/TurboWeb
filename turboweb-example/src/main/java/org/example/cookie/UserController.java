package org.example.cookie;


import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Route;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.cookie.HttpCookie;
import top.turboweb.http.core.cookie.HttpCookies;

@Route("/user")
public class UserController {

    @Get("/set")
    public String setCookie(HttpContext context) {
        context.cookies().setCookie("name", "turbo");
        return "setCookie";
    }


    @Get("/get")
    public String getCookie(HttpContext context) {
        String name = context.cookies().getCookie("name");
        return "name: " + name;
    }


    @Get("/rem")
    public String removeCookie(HttpContext context) {
        context.cookies().removeCookie("name");
        return "remCookie";
    }


    @Get("/set2")
    public String setCookieWithAttributes(HttpContext context) {
        HttpCookie cookie = new HttpCookie("name", "turbo");
        cookie.setMaxAge(3600);        // 1小时后过期
        cookie.setPath("/user");       // 限定路径
        context.cookies().setCookie(cookie);
        return "setCookie2";
    }


    @Get("/clear")
    public String clearToWriteCookies(HttpContext context) {
        HttpCookies cookies = context.cookies();
        cookies.setCookie("a", "1");
        cookies.setCookie("b", "2");
        cookies.setCookie("c", "3");
        cookies.clearToWriteCookies();
        return "clearCookie";
    }


    @Get("/clearAll")
    public String clearAllCookies(HttpContext context) {
        context.cookies().clearAll();
        return "clearAllCookie";
    }

}
