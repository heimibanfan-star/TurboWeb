package org.example.gateway;


import top.turboweb.anno.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "Hello User";
    }
}
