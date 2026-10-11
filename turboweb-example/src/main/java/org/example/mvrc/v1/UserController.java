package org.example.mvrc.v1;

import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v1版本";
    }
}
