package org.example.controller;


import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("order")
public class OrderController {

    @Get
    public String order(HttpContext context) {
        return "Hello Order";
    }
}
