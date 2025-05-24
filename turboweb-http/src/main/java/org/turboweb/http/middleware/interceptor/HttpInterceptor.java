package org.turboweb.http.middleware.interceptor;

import org.turboweb.http.context.HttpContext;

/**
 * http请求的拦截器 (不推荐在反应式编程中使用)
 */
public interface HttpInterceptor {

	/**
	 * 请求处理前调用
	 *
	 * @param ctx 上下文
	 * @return 是否继续处理
	 */
	boolean preHandler(HttpContext ctx);

	/**
	 * 请求处理后调用
	 *
	 * @param ctx 上下文
	 * @param result 处理结果
	 */
	void postHandler(HttpContext ctx, Object result);

	/**
	 * 请求处理完成后调用
	 *
	 * @param ctx 上下文
	 * @param e 异常
	 */
	void afterCompletion(HttpContext ctx, Exception e);
}
