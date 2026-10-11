package org.example.gateway;

import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;

@Route("/order")
public class OrderController {

    @Get
    public String getOrder() {
        return "get order";
    }
}
