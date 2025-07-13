package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.response.AsyncFileResponse;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;

import java.io.File;
import java.time.LocalDate;

/**
 * TODO
 */
public class UserController extends LambdaRouterGroup {

    @Override
    public String requestPath() {
        return "/user";
    }

    @Override
    protected void registerRoute(LambdaRouterGroup.RouterRegister register) {
        register.get("/", this::hello);
        register.post("/", this::doPost);
    }

    public String hello(HttpContext ctx) {
        return "Hello World";
    }

    public String doPost(HttpContext ctx) {
        return "doPost";
    }
}
