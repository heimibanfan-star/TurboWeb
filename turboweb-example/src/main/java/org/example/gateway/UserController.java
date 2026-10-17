package org.example.gateway;

import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String getUser() {
        return "get user";
    }
}
