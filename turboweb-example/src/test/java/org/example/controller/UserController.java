package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.AsyncFileResponse;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;

import java.io.File;
import java.time.LocalDate;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    @Get
    public String hello(HttpContext context) {
        return "Hello User";
    }
}
