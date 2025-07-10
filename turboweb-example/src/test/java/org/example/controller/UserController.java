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

    @Get
    public HttpFileResult download(HttpContext c) {
        File file = new File("E:\\tmp\\01.001-为什么学习并发.mp4");
        return HttpFileResult.file(file, "video/mp4", true);
    }
}
