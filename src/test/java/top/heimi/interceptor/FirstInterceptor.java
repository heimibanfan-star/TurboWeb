package top.heimi.interceptor;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.interceptor.HttpInterceptor;

/**
 * TODO
 */
public class FirstInterceptor implements HttpInterceptor {
	@Override
	public boolean preHandler(HttpContext ctx) {
		System.out.println("first intercepto prer");
		return true;
	}

	@Override
	public void postHandler(HttpContext ctx, Object result) {
		System.out.println("first interceptor after");
	}

	@Override
	public void afterCompletion(HttpContext ctx, Exception e) {
		System.out.println("first interceptor afterCompletion");
	}
}
