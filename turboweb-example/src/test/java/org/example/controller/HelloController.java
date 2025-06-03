package org.example.controller;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
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

	private ReentrantLock lock = new ReentrantLock();

	@Get("/download")
	public HttpResponse download(HttpContext c) {
		return new FileStreamResponse(new File("C:\\Users\\heimi\\Downloads\\ideaIU-2024.3.6.exe"));
	}

	@Get("/sse")
	public HttpResponse sse(HttpContext c) throws InterruptedException {
		SseEmitter sseEmitter = c.createSseEmitter();
		Thread.ofVirtual().start(() -> {
			for (int i = 0; i < 20; i++) {
                try {
					test();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
				sseEmitter.send("hello" + i);
            }
		});
		Thread.sleep(5000);
		return sseEmitter;
	}

	private synchronized void test() throws InterruptedException {
		System.out.println("aaa");
		Thread.sleep(2000);
	}

	@Post("/upload")
	public String upload(HttpContext c) throws IOException {
		FileUpload fileUpload = c.loadFile("file");
		System.out.println(fileUpload);
		fileUpload.renameTo(new File("E:/tmp/123.png"));
		return "ok";
	}
}
