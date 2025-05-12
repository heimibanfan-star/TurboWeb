package top.heimi.middlewares;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * 第一个中间件
 */
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		if (ctx != null) {
			return ctx.json("hello world");
		} else {
			return ctx.end();
		}
	}
}
