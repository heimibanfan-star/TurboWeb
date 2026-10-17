package org.example.mvrc.v2;

import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v2版本";
    }
}
