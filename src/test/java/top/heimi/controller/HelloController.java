package top.heimi.controller;

import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.MixedFileUpload;
import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.FileRegionResponse;
import org.turbo.web.core.http.response.SseResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * TODOd
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public String hello(HttpContext ctx) {
        return "hello world";
    }

    @Get("/download")
    public FileRegionResponse download(HttpContext ctx) {
        ctx.text("hello");
        String path = "D:\\java学习资料\\1.数据结构与算法\\视频(上篇)\\1、基础数据结构\\Java数据结构与算法课程导学.mp4";
        File file = new File(path);
        return new FileRegionResponse(file);
    }

//    @Get("/sse")
//    public Mono<SseResponse> sse(HttpContext ctx) {
//        SseResponse sseResponse = ctx.newSseResponse();
//        Flux<Long> flux = Flux.interval(Duration.ofSeconds(1))
//            .take(10);
//        sseResponse.setSseCallback(flux);
//        return Mono.just(sseResponse);
//    }

    @Post
    public void upload(HttpContext ctx) throws IOException {
        FileUpload fileUpload = ctx.loadFile("file");
        System.out.println(fileUpload);
        File file = File.createTempFile("HeiMi", ".tmp");
        fileUpload.renameTo(file);
        ctx.text("upload");
    }
}
