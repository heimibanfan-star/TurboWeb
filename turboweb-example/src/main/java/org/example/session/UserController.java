package org.example.session;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookie;
import top.turboweb.http.session.HttpSession;

@RequestPath("/user")
public class UserController {

    @Get("/set")
    public String setSession(HttpContext context) {
        HttpSession httpSession = context.httpSession();
        httpSession.setAttr("name", "turboweb");
        return "setSession";
    }

    @Get("/get")
    public String getSession(HttpContext context) {
        HttpSession httpSession = context.httpSession();
        String name = httpSession.getAttr("name", String.class);
        return "getSession: " + name;
    }

    @Get("/rem")
    public String removeSession(HttpContext context) {
        HttpSession httpSession = context.httpSession();
        httpSession.remAttr("name");
        return "removeSession";
    }

    @Get("/setttl")
    public String setSessionTTL(HttpContext context) {
        HttpSession httpSession = context.httpSession();
        httpSession.setAttr("name", "turboweb", 10000);
        return "setSessionTTL";
    }
}
