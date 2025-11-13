package org.example.mvrc.v1;

import top.turboweb.anno.method.Get;
import top.turboweb.anno.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v1版本";
    }
}
