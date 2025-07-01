package org.example.controller;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.FileStreamResponse;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;

import java.io.File;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get("/{name:str}/{id:num}")
	public String get(HttpContext ctx) {
		Long id = ctx.paramLong("id");
		System.out.println(id);
		return "hello world";
	}

	@Get("/sse")
	public SseResponse sse(HttpContext ctx) {
		SseResponse sseResponse = ctx.createSseResponse();
		sseResponse.setSseCallback((session) -> {
			for (int i = 0; i < 10; i++) {
				session.send("hello:" + i);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		});
		return sseResponse;
	}

	@Get("/sse2")
	public SseEmitter sse2(HttpContext ctx) {
		SseEmitter sseEmitter = ctx.createSseEmitter();
		Thread.ofVirtual().start(() -> {
			for (int i = 0; i < 10; i++) {
				sseEmitter.send("hello" + i);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
			sseEmitter.close();
		});
		return sseEmitter;
	}

	@Get("/download")
	public HttpResponse download(HttpContext ctx) {
		File file = new File("D:\\java学习资料\\前端\\1.JavaScript基础\\视频\\第1天视频\\JS基础Day1-00-大展宏“兔”课程必读.mp4");
		return new FileStreamResponse(file);
	}
}
