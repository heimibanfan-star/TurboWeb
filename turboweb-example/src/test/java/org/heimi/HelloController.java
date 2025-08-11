package org.heimi;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Param;
import top.turboweb.commons.anno.QueryModel;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.FileStreamResponse;
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

    @Get("/{id:int}")
    public String hello(@Param("id") Long id, @QueryModel User user) {
        System.out.println(id);
        System.out.println(user);
        return "Hello User";
    }

    @Get
    public String test() {
        return "Hello World";
    }
}
