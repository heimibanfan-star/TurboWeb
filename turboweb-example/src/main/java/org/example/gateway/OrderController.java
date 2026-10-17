package org.example.gateway;

import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/order")
public class OrderController {

    @Get
    public String getOrder() {
        return "get order";
    }
}
