package org.turboweb.core.http.middleware.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.http.context.HttpContext;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.middleware.interceptor.HttpInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截器中间件
 */
public class InterceptorMiddleware extends Middleware {

	private static final Logger log = LoggerFactory.getLogger(InterceptorMiddleware.class);
	private final List<HttpInterceptor> interceptors = new ArrayList<>();
	private boolean isLocked = false;

	@Override
	public Object invoke(HttpContext ctx) {
		Exception exception = null;
		int index = -1;
		Object result = null;
		try {
			// 执行前置拦截器
			boolean flag = true;
			while (index + 1 < interceptors.size() && flag) {
				HttpInterceptor interceptor = interceptors.get(index + 1);
				flag = interceptor.preHandler(ctx);
				if (flag) {
					index++;
				}
			}
			if (flag) {
				result = next(ctx);
				for (int i = index; i >= 0; i--) {
					interceptors.get(i).postHandler(ctx, result);
				}
			}
		} catch (RuntimeException e) {
			exception = e;
			throw e;
		} finally {
			for (int i = index; i >= 0; i--) {
				interceptors.get(i).afterCompletion(ctx, exception);
			}
		}
		return result;
	}

	/**
	 * 添加拦截器
	 *
	 * @param interceptor 拦截器
	 */
	public void addLast(HttpInterceptor interceptor) {
		if (isLocked) {
			log.warn("InterceptorMiddleware is locked, can't add interceptor at target");
		}
		interceptors.add(interceptor);
	}

	/**
	 * 添加拦截器
	 *
	 * @param interceptor 拦截器
	 */
	public void addFirst(HttpInterceptor interceptor) {
		if (isLocked) {
			log.warn("InterceptorMiddleware is locked, can't add interceptor at target");
		}
		interceptors.addFirst(interceptor);
	}

	/**
	 * 添加拦截器
	 *
	 * @param interceptor 拦截器
	 * @param target      目标拦截器
	 */
	public void addAfter(HttpInterceptor interceptor, HttpInterceptor target) {
		if (isLocked) {
			log.warn("InterceptorMiddleware is locked, can't add interceptor at target");
		}
		int index = interceptors.indexOf(target);
		if (index != -1) {
			interceptors.add(index + 1, interceptor);
		}
	}

	/**
	 * 添加拦截器
	 *
	 * @param interceptor 拦截器
	 * @param target      目标拦截器
	 */
	public void addBefore(HttpInterceptor interceptor, HttpInterceptor target) {
		if (isLocked) {
			log.warn("InterceptorMiddleware is locked, can't add interceptor at target");
		}
		int index = interceptors.indexOf(target);
		if (index != -1) {
			interceptors.add(index, interceptor);
		}
	}

	/**
	 * 添加拦截器
	 *
	 * @param interceptor 拦截器
	 * @param target      目标拦截器
	 */
	public void addAt(HttpInterceptor interceptor, HttpInterceptor target) {
		if (isLocked) {
			log.warn("InterceptorMiddleware is locked, can't add interceptor at target");
		}
		int index = interceptors.indexOf(target);
		if (index != -1) {
			interceptors.set(index, interceptor);
		}
	}

	@Override
	public void init(Middleware chain) {
		super.init(chain);
		isLocked = true;
		log.info("interceptor locked finish");
	}
}
