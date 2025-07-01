package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    @Get
    public String hello(HttpContext c) {
        return "Hello World";
    }

    @Get("/{name:str}")
    public String showName(HttpContext c) {
        int i = 1/0;
        return "name:" + c.param("name");
    }
}
