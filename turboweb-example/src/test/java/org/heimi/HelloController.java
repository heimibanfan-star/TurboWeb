package org.heimi;

import reactor.core.publisher.Flux;
import top.turboweb.anno.Get;
import top.turboweb.anno.Route;

@Route("/hello")
public class HelloController {

    @Get("/1")
    public String hello() {
        return "Hello World";
    }

    @Get("/2")
    public Integer num() {
        return 10;
    }

    @Get("/3")
    public Flux<String> stream() {
        return Flux.just("你好", "世界");
    }
}
