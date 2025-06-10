package top.turboweb.core.server;

import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.gateway.Gateway;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.core.initializer.*;
import top.turboweb.core.initializer.impl.*;

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
    public void controller(Object controller, Class<?> originClass) {
        middlewareInitializer.addController(controller, originClass);
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
	public HttpProtocolDispatcher create(Class<?> mainClass, ServerParamConfig config) {
		ExceptionHandlerMatcher handlerMatcher = exceptionHandlerInitializer.init();
		SessionManagerHolder sessionManagerHolder = sessionManagerProxyInitializer.init(config);
		Middleware chain = middlewareInitializer.init(sessionManagerHolder, mainClass, handlerMatcher, config);
		HttpScheduler httpScheduler = httpSchedulerInitializer.init(sessionManagerHolder, handlerMatcher, chain, config);
		return new HttpProtocolDispatcher(
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
