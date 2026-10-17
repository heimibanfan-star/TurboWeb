package org.example.gateway;

import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String getUser() {
        return "get user";
    }
}
