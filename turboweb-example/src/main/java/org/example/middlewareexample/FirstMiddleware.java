package org.example.middlewareexample;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

/**
 * 中间件
 */
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		System.out.println("first 调用下一个中间件之前");
		try {
			Object result = next(ctx);
			System.out.println("first 调用下一个中间件之后");
			return result;
		} finally {
			System.out.println("finally");
		}
	}
}
