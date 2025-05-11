package org.turbo.web.core.server.alpha;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.handler.piplines.HttpWorkerDispatcherHandler;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.ws.WebSocketHandler;

/**
 * http工作分发器的代理对象
 */
public interface HttpWorkDispatcherFactory {

	/**
	 * 添加控制器
	 * @param controllers 控制器
	 */
	void controllers(Object... controllers);

	/**
	 * 添加中间件
	 * @param middlewares 中间件
	 */
	void middlewares(Middleware... middlewares);

	/**
	 * 添加异常处理器
	 * @param exceptionHandlers 异常处理器
	 */
	void exceptionHandlers(Object... exceptionHandlers);

	/**
	 * 添加websocket处理器
	 * @param path websocket处理器的路径
	 * @param webSocketHandler websocket处理器
	 */
	void websocketHandler(String path, WebSocketHandler webSocketHandler);

	/**
	 * 设置网关
	 * @param gateway 网关
	 */
	void gateway(Gateway gateway);

	/**
	 * 使用反应式
	 */
	void useReactive();

	/**
	 * 创建http工作分发器
	 * @return http工作分发器
	 */
	HttpWorkerDispatcherHandler create(Class<?> mainClass, ServerParamConfig config);

	/**
	 * 替换session管理器
	 * @param sessionManager session管理器
	 */
	void replaceSessionManager(SessionManager sessionManager);

}
