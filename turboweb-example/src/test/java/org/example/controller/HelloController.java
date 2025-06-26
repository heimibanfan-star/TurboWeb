package org.example.controller;

import reactor.core.publisher.Flux;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;

import java.time.Duration;

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
		SseResponse sseResponse = ctx.newSseResponse();
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
}
