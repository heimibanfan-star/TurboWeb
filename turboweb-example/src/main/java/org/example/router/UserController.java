package org.example.router;


import top.turboweb.http.core.annotation.router.Get;
import top.turboweb.http.core.annotation.router.Route;
import top.turboweb.http.context.HttpContext;

@Route("/user")
public class UserController {
    @Get
    public String getUser(HttpContext context) {
        return "Get User";
    }
}
