package top.heimi.controllers;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.Patch;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.ChunkedFileResponse;
import org.turbo.web.core.http.response.FileRegionResponse;

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

	@Patch("/{id}")
	public Object hello2(HttpContext ctx) {
		Long id = ctx.paramLong("id");
		System.out.println(id);
		return "hello world";
	}

	@Get("/down")
	public HttpResponse download(HttpContext c) {
//		String filePath = "D:\\java学习资料\\1.数据结构与算法\\视频(上篇)\\1、基础数据结构\\Java数据结构与算法课程导学.mp4";
		String filePath = "C:\\Users\\heimi\\Downloads\\goland-2024.3.exe";
		ChunkedFileResponse fileResponse = new ChunkedFileResponse(new File(filePath), 1024 * 1024);
		fileResponse.listener((future, progress, total) -> {
			System.err.println("进度：" + progress * 1.0 / total * 100 + "%") ;
		}, future -> {
			System.out.println("下载完成");
		});
		return fileResponse;
	}
}
