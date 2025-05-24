package top.heimi.controllers;

import io.netty.handler.codec.http.HttpResponse;
import org.turboweb.anno.Get;
import org.turboweb.anno.RequestPath;
import org.turboweb.core.http.context.HttpContext;
import org.turboweb.core.http.response.FileStreamResponse;
import org.turboweb.core.http.response.sync.SseEmitter;

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
		String path = "C:\\Users\\heimi\\Downloads\\temp";  // 28GB
		FileStreamResponse fileResponse = new FileStreamResponse(new File(path), 1024 * 1024 * 10, true);
		return fileResponse;
	}

	@Get("/sse")
	public SseEmitter sse(HttpContext c) throws InterruptedException {
		SseEmitter sseEmitter = c.newSseEmitter();
		Thread.ofVirtual().start(() -> {
			for (int i = 0; i < 10; i++) {
				sseEmitter.send("hello world" + i);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			sseEmitter.close();
		});
		sseEmitter.onClose(obj -> {
			System.out.println("close:" + (obj == sseEmitter));
		});
		Thread.sleep(5000);
		return sseEmitter;
	}

}
