package org.example.controller;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.sync.SseEmitter;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

	@Get
	public String helloUser(HttpContext c) {
		return "hello user";
	}

	@Get("/sse")
	public HttpResponse sse(HttpContext c) {
		SseEmitter sseEmitter = c.createSseEmitter();
		Thread.ofVirtual().start(() -> {
			for (int i = 0; i < 10; i++) {
				sseEmitter.send("hello" + i);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
			sseEmitter.close();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
		return sseEmitter;
	}

	@Get("/gc")
	public String gc(HttpContext c) {
		System.gc();
		return "ok";
	}
}
