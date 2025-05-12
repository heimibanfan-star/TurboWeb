package org.turbo.web.core.server;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.handler.piplines.HttpWorkerDispatcherHandler;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.scheduler.HttpScheduler;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.initializer.*;
import org.turbo.web.core.initializer.impl.*;

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
