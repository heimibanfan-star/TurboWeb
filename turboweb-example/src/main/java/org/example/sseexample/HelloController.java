package org.example.sseexample;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.response.sync.SseEmitter;

@RequestPath("/hello")
public class HelloController {

	@Get("/example01")
	public HttpResponse example01(HttpContext c) {
		SseEmitter sseEmitter = c.newSseEmitter(128);
		Thread.ofVirtual().start(() -> {
			for (int i = 0; i < 10; i++) {
				sseEmitter.send("hello:" + i);
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

	@Get("/example02")
	public HttpResponse example02(HttpContext c) {
		SseResponse sseResponse = c.newSseResponse();
		sseResponse.setSseCallback((session) -> {
			for (int i = 0; i < 10; i++) {
				session.send("hello:" + i);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			session.close();
		});
		return sseResponse;
	}
}
