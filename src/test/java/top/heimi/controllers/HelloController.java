package top.heimi.controllers;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.FileRegionResponse;
import org.turbo.web.core.http.response.FileStreamResponse;
import org.turbo.web.core.http.response.SseResponse;

import java.io.File;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get("/{id}/{age}/{sex}")
	public Object hello(HttpContext ctx) {
		Long id = ctx.paramLong("id");
		Integer age = ctx.paramInt("age");
		Boolean sex = ctx.paramBoolean("sex");
		System.out.println(id);
		System.out.println(age);
		System.out.println(sex);
		return "hello world";
	}

	@Get
	public Object hello2(HttpContext ctx) {
		return "hello world";
	}

	@Get("/down")
	public HttpResponse download(HttpContext c) {
//		String filePath = "D:\\java学习资料\\1.数据结构与算法\\视频(上篇)\\1、基础数据结构\\Java数据结构与算法课程导学.mp4";
		String filePath = "C:\\Users\\heimi\\Downloads\\goland-2024.3.exe";
		FileRegionResponse fileResponse = new FileRegionResponse(new File(filePath));
		return fileResponse;
	}

	@Get("/sse")
	public HttpResponse sse(HttpContext ctx) {
		SseResponse sseResponse = ctx.newSseResponse();
		sseResponse.setSseCallback(session -> {
			for (int i = 0; i < 10; i++) {
				session.send("hello world" + i);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		});
		return sseResponse;
	}
}
