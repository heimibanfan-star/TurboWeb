package org.example.lifecycle;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.aware.SessionManagerProxyAware;
import top.turboweb.http.session.SessionManagerProxy;

public class MyMiddleware extends Middleware implements SessionManagerProxyAware {
	@Override
	public Object invoke(HttpContext ctx) {
		return next(ctx);
	}

	@Override
	public void setSessionManagerProxy(SessionManagerProxy sessionManagerProxy) {
		System.out.println(sessionManagerProxy);
		System.out.println("注入Session管理器代理对象");
	}

	@Override
	public void init(Middleware chain) {
		System.out.println("初始化中间件");
	}
}
