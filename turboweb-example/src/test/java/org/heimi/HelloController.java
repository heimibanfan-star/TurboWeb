package org.heimi;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.ZeroCopyResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public Flux<String> hello(HttpContext context) throws InterruptedException, IOException {
        return Flux.<String>create(sink -> {
                    Thread.ofVirtual().start(() -> {
                        for (int i = 0; i < 10; i++) {
                            sink.next("hello " + i);
                        }
                        sink.complete();
                    });
                })
                .delayElements(Duration.ofMillis(500));
//        return Mono.just("Hello World");
    }

    @Get("/1")
    public String helloWorld(HttpContext context) {
        return "helloWorld";
    }

    @Get("/2")
    public HttpResponse helloWorld2(HttpContext context) {
        File file = new File("E:\\tmp\\evection.png");
        return new ZeroCopyResponse( file);
    }
}
