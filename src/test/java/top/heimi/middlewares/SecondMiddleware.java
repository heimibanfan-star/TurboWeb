package top.heimi.middlewares;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class SecondMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		System.out.println("SecondMiddleware 执行之前。。。");
		Object result = next(ctx);
		System.out.println("SecondMiddleware 执行之后。。。");
		return result;
	}
}
