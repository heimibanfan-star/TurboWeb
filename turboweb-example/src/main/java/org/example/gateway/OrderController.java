package org.example.gateway;

import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Route;

@Route("/order")
public class OrderController {

    @Get
    public String getOrder() {
        return "get order";
    }
}
