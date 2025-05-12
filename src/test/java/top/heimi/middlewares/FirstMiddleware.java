package top.heimi.middlewares;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * 第一个中间件
 */
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		System.out.println("FirstMiddleware 执行之前。。。");
		Object result = next(ctx);
		System.out.println("FirstMiddleware 执行之后。。。");
		return result;
	}
}
