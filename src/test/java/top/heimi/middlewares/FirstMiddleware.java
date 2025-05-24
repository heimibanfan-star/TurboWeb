package top.heimi.middlewares;

import org.turboweb.core.http.context.HttpContext;
import org.turboweb.core.http.middleware.Middleware;
import top.heimi.pojos.User;

/**
 * 第一个中间件
 */
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		User user = ctx.loadQuery(User.class);
		if (user.getAge() >= 18) {
			return ctx.json(user);
		} else {
			return ctx.end();
		}
	}
}
