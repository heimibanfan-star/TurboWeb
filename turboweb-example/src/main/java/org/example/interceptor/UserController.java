package org.example.interceptor;

import top.turboweb.anno.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String getUser(HttpContext ctx) {
        System.out.println("getUser");
        return "user";
    }
}
