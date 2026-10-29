package org.example.mvrc.v2;

import top.turboweb.http.core.annotation.router.Get;
import top.turboweb.http.core.annotation.router.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v2版本";
    }
}
