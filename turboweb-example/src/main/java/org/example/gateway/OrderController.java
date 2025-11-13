package org.example.gateway;

import top.turboweb.anno.method.Get;
import top.turboweb.anno.Route;

@Route("/order")
public class OrderController {

    @Get
    public String getOrder() {
        return "get order";
    }
}
