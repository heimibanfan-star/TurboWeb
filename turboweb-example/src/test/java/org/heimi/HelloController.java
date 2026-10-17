package org.heimi;

import reactor.core.publisher.Flux;
import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Route;
import top.turboweb.http.core.response.HttpFileResult;

import java.io.FileInputStream;
import java.io.IOException;

@Route("/hello")
public class    HelloController {

    @Get("/1")
    public String hello() {
        return "Hello World";
    }

    @Get("/2")
    public HttpFileResult num() throws IOException {
        FileInputStream is = new FileInputStream("E://temp/bg.jpg");
        try (is) {
            byte[] bytes = is.readAllBytes();
            return HttpFileResult.jpeg(bytes);
        }
    }

    @Get("/3")
    public Flux<String> stream() {
        return Flux.just("你好", "世界");
    }

}
