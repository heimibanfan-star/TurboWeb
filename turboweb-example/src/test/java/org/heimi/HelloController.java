package org.heimi;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public String hello(HttpContext context) throws InterruptedException {
        System.out.println("hello world");
        TimeUnit.SECONDS.sleep(8);
        return "hello world";
    }
}
