package org.example.quickstart;


import top.turboweb.anno.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/hello")
public class HelloController {

    @Get
    public String hello(HttpContext context) {
        return "Hello World";
    }
}
