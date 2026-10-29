package org.example.gateway;

import top.turboweb.http.core.annotation.router.Get;
import top.turboweb.http.core.annotation.router.Route;

@Route("/order")
public class OrderController {

    @Get
    public String getOrder() {
        return "get order";
    }
}
