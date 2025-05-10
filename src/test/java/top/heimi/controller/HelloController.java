package top.heimi.controller;

import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.MixedFileUpload;
import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.FileRegionResponse;
import org.turbo.web.core.http.response.SseResponse;
import org.turbo.web.core.http.response.ViewModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;

/**
 * TODOd
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public String hello(HttpContext ctx) {
        System.out.println("hello");
        return "hello world";
    }

    @Get("/download")
    public void download(HttpContext ctx) throws FileNotFoundException {
        String path = "E:\\javaCodeDev\\turbo-web\\src\\test\\resources\\static\\img.png";
        ctx.fileHelper().png(new FileInputStream(path));
    }

    @Get("/view")
    public ViewModel view(HttpContext ctx) {
        ViewModel viewModel = new ViewModel();
        viewModel.setViewName("index");
        viewModel.addAttribute("name", "turbo");
        return viewModel;
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
