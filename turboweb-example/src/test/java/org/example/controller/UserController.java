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

    public record User(String name, int age) {}

//    @Get("/{name:str}")
//    public String name(HttpContext ctx) {
//        String name = ctx.param("name");
//        System.out.println(name);
//        return "hello world";
//    }
//
//    @Get("/{age:str}")
//    public String age(HttpContext ctx) {
//        int age = ctx.paramInt("age");
//        System.out.println(age);
//        return "hello world";
//    }

//    @Get("/{money:num}")
//    public String money(HttpContext ctx) {
//        double money = ctx.paramDouble("money");
//        System.out.println(money);
//        return "hello world";
//    }

//    @Get("/{date:date}")
//    public String date(HttpContext ctx) {
//        LocalDate date = ctx.paramDate("date");
//        System.out.println(date);
//        return "hello world";
//    }
//
//    @Get("/{bool:bool}")
//    public String bool(HttpContext ctx) {
//        boolean bool = ctx.paramBool("bool");
//        System.out.println(bool);
//        return "hello world";
//    }
//
    @Get("/{ip:regex=hello}")
    public String ip(HttpContext ctx) {
        String ip = ctx.param("ip");
        System.out.println(ip);
        return "hello world";
    }
}
