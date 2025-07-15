package org.example.router;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String getUser(HttpContext context) {
        return "Get User";
    }
}
