package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.AsyncFileResponse;
import top.turboweb.http.response.HttpResult;

import java.io.File;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    public record User(String name, int age) {}

    @Get("/set")
    public String setCookie(HttpContext c) {
        c.httpSession().setAttr("name", "turboweb", 10000);
        return "setSession";
    }

    @Get("/get")
    public String getCookie(HttpContext c) {
        String name = c.httpSession().getAttr("name", String.class);
        return "getSession: " + name;
    }

    @Get("/rem")
    public String removeCookie(HttpContext c) {
        c.httpSession().remAttr("name");
        return "remSession";
    }
}
