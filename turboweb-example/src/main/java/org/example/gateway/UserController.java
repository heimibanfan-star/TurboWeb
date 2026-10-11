package org.example.gateway;

import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;

@Route("/user")
public class UserController {

    @Get
    public String getUser() {
        return "get user";
    }
}
