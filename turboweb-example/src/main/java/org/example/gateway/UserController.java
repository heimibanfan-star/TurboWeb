package org.example.gateway;

import top.turboweb.anno.Get;
import top.turboweb.anno.Route;

@Route("/user")
public class UserController {

    @Get
    public String getUser() {
        return "get user";
    }
}
