package org.example.mvrc.v1;

import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v1版本";
    }
}
