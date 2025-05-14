package top.heimi.controllers;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.FileStreamResponse;

import java.io.File;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get
	public Object hello2(HttpContext ctx) throws InterruptedException {
		return "hello world";
	}

	@Get("/download")
	public HttpResponse download(HttpContext c) {
//		String path = "C:\\Users\\heimi\\Downloads\\temp";
		String path = "C:\\Users\\heimi\\Downloads\\video.mp4";
		FileStreamResponse fileResponse = new FileStreamResponse(new File(path), 1024 * 1024);
		return fileResponse;
	}

}
