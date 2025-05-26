package org.example.middlewareexample;

import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.AbstractConcurrentLimitMiddleware;
import top.turboweb.http.middleware.AbstractGlobalConcurrentLimitMiddleware;
import top.turboweb.http.middleware.CorsMiddleware;
import top.turboweb.http.middleware.ServerInfoMiddleware;
import top.turboweb.http.middleware.sync.FreemarkerTemplateMiddleware;
import top.turboweb.http.middleware.sync.InterceptorMiddleware;
import top.turboweb.http.middleware.sync.StaticResourceMiddleware;

public class MiddlewareApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(MiddlewareApplication.class);
		server.controllers(new UserController());
//		server.middlewares(new FirstMiddleware(), new SecondMiddleware());
//		server.middlewares(new SecondMiddleware(), new FirstMiddleware());
//		InterceptorMiddleware interceptorMiddleware = new InterceptorMiddleware();
//		interceptorMiddleware.addLast(new FirstInterceptor());
//		interceptorMiddleware.addLast(new SecondInterceptor());
//		server.middlewares(interceptorMiddleware);
//		server.middlewares(new StaticResourceMiddleware());
//		server.middlewares(new FreemarkerTemplateMiddleware());
//		CorsMiddleware corsMiddleware = new CorsMiddleware();
//		server.middlewares(corsMiddleware);
//		ServerInfoMiddleware serverInfoMiddleware = new ServerInfoMiddleware();
//		server.middlewares(serverInfoMiddleware);
		AbstractGlobalConcurrentLimitMiddleware globalConcurrentLimitMiddleware = new AbstractGlobalConcurrentLimitMiddleware(10) {
			@Override
			public Object doAfterReject(HttpContext ctx) {
				return "reject";
			}
		};
		AbstractConcurrentLimitMiddleware concurrentLimitMiddleware = new AbstractConcurrentLimitMiddleware() {
			@Override
			public Object doAfterReject(HttpContext ctx) {
				return "second reject";
			}
		};
		concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/user/example06", 1);
		server.middlewares(globalConcurrentLimitMiddleware, concurrentLimitMiddleware);
		server.start();
	}
}
