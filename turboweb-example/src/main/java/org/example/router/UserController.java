package org.example.router;


import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;
import top.turboweb.http.context.HttpContext;

@Route("/user")
public class UserController {
    @Get
    public String getUser(HttpContext context) {
        return "Get User";
    }
}
