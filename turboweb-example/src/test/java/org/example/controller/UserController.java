package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpResult;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    public record User(String name, int age) {}

    @Get
    public HttpResult<User> hello(HttpContext c) {
        return HttpResult.create(500, new User("turbo", 18));
    }

    @Get("/{name:str}")
    public String showName(HttpContext c) {
        return "name:" + c.param("name");
    }
}
