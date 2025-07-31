package org.heimi;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
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
import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public HttpResponse hello(HttpContext context) throws InterruptedException, IOException {
//        FileInputStream fis = new FileInputStream("E:\\tmp\\evection.png");
//        FileChannel fileChannel = fis.getChannel();
//        InternalConnectSession connectSession = (InternalConnectSession) context.getConnectSession();
//        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
//        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "image/png");
//        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, fileChannel.size());
//        connectSession.getChannel().writeAndFlush(response);
//        EventLoop eventExecutors = connectSession.getChannel().eventLoop();
//        System.out.println(eventExecutors);
//        connectSession.getChannel().writeAndFlush(new DefaultFileRegion(fileChannel, 0, fileChannel.size()));
        File file = new File("E:\\tmp\\evection.png");
        ZeroCopyResponse response = new ZeroCopyResponse(file);
        response.setContentType(ContentType.IMAGE_PNG);
        // 在浏览器直接打开
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline;filename=\"" + file.getName() + "\"");
        return response;
    }

    @Get("/1")
    public String helloWorld(HttpContext context) {
        return "helloWorld";
    }

    @Get("/2")
    public HttpFileResult helloWorld2(HttpContext context) {
        File file = new File("E:\\tmp\\evection.png");
        return HttpFileResult.file(file);
    }
}
