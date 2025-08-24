package org.example.gateway;


import top.turboweb.anno.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/order")
public class OrderController {
    @Get
    public String order(HttpContext context) {
        return "Hello Order";
    }
}
