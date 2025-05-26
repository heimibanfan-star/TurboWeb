package org.example.middlewareexample;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.interceptor.HttpInterceptor;

public class FirstInterceptor implements HttpInterceptor {
	@Override
	public boolean preHandler(HttpContext ctx) {
		System.out.println("first preHandler");
		return true;
	}
	@Override
	public void postHandler(HttpContext ctx, Object result) {
		System.out.println("first postHandler");
	}
	@Override
	public void afterCompletion(HttpContext ctx, Exception e) {
		System.out.println("first afterCompletion");
	}
}
