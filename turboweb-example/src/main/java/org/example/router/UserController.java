package org.example.router;


import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;
import top.turboweb.http.context.HttpContext;

@Route("/user")
public class UserController {
    @Get
    public String getUser(HttpContext context) {
        return "Get User";
    }
}
