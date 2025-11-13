package org.example.middleware;


import top.turboweb.anno.method.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "User";
    }
}
