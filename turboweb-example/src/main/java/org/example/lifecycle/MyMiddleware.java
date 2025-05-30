package org.example.lifecycle;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.aware.SessionManagerHolderAware;
import top.turboweb.http.session.SessionManagerHolder;

public class MyMiddleware extends Middleware implements SessionManagerHolderAware {
	@Override
	public Object invoke(HttpContext ctx) {
		return next(ctx);
	}

	@Override
	public void setSessionManagerProxy(SessionManagerHolder sessionManagerHolder) {
		System.out.println(sessionManagerHolder);
	}
}
