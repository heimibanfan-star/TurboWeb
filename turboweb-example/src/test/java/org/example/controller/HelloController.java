package org.example.controller;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.FileRegionResponse;
import top.turboweb.http.response.FileStreamResponse;
import top.turboweb.http.response.sync.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

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
}
