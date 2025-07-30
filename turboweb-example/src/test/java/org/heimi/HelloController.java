package org.heimi;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.SseEmitter;

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
    public String hello(HttpContext context) throws InterruptedException, IOException {
        FileInputStream fis = new FileInputStream("E:\\tmp\\evection.png");
        FileChannel fileChannel = fis.getChannel();
        InternalConnectSession connectSession = (InternalConnectSession) context.getConnectSession();
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "image/png");
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, fileChannel.size());
        connectSession.getChannel().writeAndFlush(response);
        EventLoop eventExecutors = connectSession.getChannel().eventLoop();
        System.out.println(eventExecutors);
        connectSession.getChannel().writeAndFlush(new DefaultFileRegion(fileChannel, 0, fileChannel.size()));
        return "hello world";
    }
}
