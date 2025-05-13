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
		FileStreamResponse fileResponse = new FileStreamResponse(new File(filePath), 512);
		return fileResponse;
	}
}
