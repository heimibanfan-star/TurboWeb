package org.example.middleware;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "User";
    }
}
