package org.example.quickstart;


import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;
import top.turboweb.http.context.HttpContext;

@Route("/hello")
public class HelloController {

    @Get
    public String hello(HttpContext context) {
        return "Hello World";
    }
}
