package top.heimi.interceptor;

import org.turboweb.core.http.context.HttpContext;
import org.turboweb.core.http.middleware.interceptor.HttpInterceptor;

/**
 * TODO
 */
public class SecondInterceptor implements HttpInterceptor {
	@Override
	public boolean preHandler(HttpContext ctx) {
		System.out.println("second interceptor preHandler");
		int i = 1/0;
		return true;
	}

	@Override
	public void postHandler(HttpContext ctx, Object result) {
		System.out.println("second interceptor postHandler");
	}

	@Override
	public void afterCompletion(HttpContext ctx, Exception e) {
		System.out.println("second interceptor afterCompletion");
	}
}
