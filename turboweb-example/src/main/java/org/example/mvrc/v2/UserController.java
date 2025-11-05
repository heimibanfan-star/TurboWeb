package org.example.mvrc.v2;

import top.turboweb.anno.Get;
import top.turboweb.anno.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v2版本";
    }
}
