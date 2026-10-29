package org.example.gateway;

import top.turboweb.http.core.annotation.router.Get;
import top.turboweb.http.core.annotation.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String getUser() {
        return "get user";
    }
}
