package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.AsyncFileResponse;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;

import java.io.File;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    public record User(String name, int age) {}

    @Get("/{name:str}")
    public String name(HttpContext ctx) {
        String name = ctx.param("name");
        System.out.println(name);
        return "hello world";
    }
}
