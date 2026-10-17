package org.example.middleware;


import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;
import top.turboweb.http.context.HttpContext;

@Route("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "User";
    }
}
