package org.example.mvrc.v2;

import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v2版本";
    }
}
