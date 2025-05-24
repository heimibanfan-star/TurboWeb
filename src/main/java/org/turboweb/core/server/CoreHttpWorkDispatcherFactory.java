package org.turboweb.core.server;

import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.gateway.Gateway;
import org.turboweb.core.initializer.*;
import org.turboweb.core.initializer.impl.*;
import org.turboweb.core.piplines.HttpWorkerDispatcherHandler;
import org.turboweb.core.http.handler.ExceptionHandlerMatcher;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.scheduler.HttpScheduler;
import org.turboweb.core.http.session.SessionManager;
import org.turboweb.core.http.session.SessionManagerProxy;
import org.turboweb.core.http.ws.WebSocketHandler;

/**
 * http工作分发器代理的核心实现类
 */
public class CoreHttpWorkDispatcherFactory implements HttpWorkDispatcherFactory {

	private final ExceptionHandlerInitializer exceptionHandlerInitializer;
	private final MiddlewareInitializer middlewareInitializer;
	private final HttpSchedulerInitializer httpSchedulerInitializer;
	private final WebSocketHandlerInitializer webSocketHandlerInitializer;
	private final SessionManagerProxyInitializer sessionManagerProxyInitializer;
	private Gateway gateway;

	{
		exceptionHandlerInitializer = new DefaultExceptionHandlerInitializer();
		middlewareInitializer = new DefaultMiddlewareInitializer();
		httpSchedulerInitializer = new DefaultHttpSchedulerInitializer();
		webSocketHandlerInitializer = new DefaultWebSocketHandlerInitializer();
		sessionManagerProxyInitializer = new DefaultSessionManagerProxyInitializer();
	}

	@Override
	public void controllers(Object... controllers) {
		middlewareInitializer.addController(controllers);
	}

	@Override
	public void middlewares(Middleware... middlewares) {
		middlewareInitializer.addMiddleware(middlewares);
	}

	@Override
	public void exceptionHandlers(Object... exceptionHandlers) {
		exceptionHandlerInitializer.addExceptionHandler(exceptionHandlers);
	}

	@Override
	public void websocketHandler(String path, WebSocketHandler webSocketHandler) {
		webSocketHandlerInitializer.setWebSocketHandler(path, webSocketHandler);
	}

	@Override
	public void websocketHandler(String path, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
		webSocketHandlerInitializer.setWebSocketHandler(path, webSocketHandler);
		webSocketHandlerInitializer.setForkJoinThreadNum(forkJoinThreadNum);
	}

	@Override
	public void gateway(Gateway gateway) {
		this.gateway = gateway;
	}

	@Override
	public void useReactive() {
		httpSchedulerInitializer.isReactive(true);
	}

	@Override
	public HttpWorkerDispatcherHandler create(Class<?> mainClass, ServerParamConfig config) {
		ExceptionHandlerMatcher handlerMatcher = exceptionHandlerInitializer.init();
		SessionManagerProxy sessionManagerProxy = sessionManagerProxyInitializer.init(config);
		Middleware chain = middlewareInitializer.init(sessionManagerProxy, mainClass, handlerMatcher, config);
		HttpScheduler httpScheduler = httpSchedulerInitializer.init(sessionManagerProxy, handlerMatcher, chain, config);
		return new HttpWorkerDispatcherHandler(
			httpScheduler,
			webSocketHandlerInitializer.isUse()? webSocketHandlerInitializer.init() : null,
			webSocketHandlerInitializer.isUse()? webSocketHandlerInitializer.getPath() : null,
			this.gateway
		);
	}

	@Override
	public void replaceSessionManager(SessionManager sessionManager) {
		sessionManagerProxyInitializer.setSessionManager(sessionManager);
	}
}
