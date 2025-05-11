package org.turbo.web.core.server.alpha;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.listener.TurboServerListener;

import java.util.function.Consumer;

/**
 * turboWeb的核心启动接口
 */
public interface TurboWebServer {

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
	 * 添加配置
	 * @param consumer 配置
	 */
	void config(Consumer<ServerParamConfig> consumer);

	/**
	 * 使用响应式服务器
	 */
	void useReactiveServer();

	/**
	 * 设置网关
	 * @param gateway 网关
	 */
	void gateway(Gateway gateway);

	/**
	 * 添加websocket处理器
	 * @param pathRegex 路径正则
	 * @param webSocketHandler websocket处理器
	 */
	void websocket(String pathRegex, WebSocketHandler webSocketHandler);

	/**
	 * 执行默认的监听器
	 * @param flag 是否执行
	 */
	void executeDefaultListener(boolean flag);

	/**
	 * 添加监听器
	 * @param listeners 监听器
	 */
	void listeners(TurboServerListener... listeners);

	/**
	 * 替换sessionManager
	 * @param sessionManager sessionManager
	 */
	void replaceSessionManager(SessionManager sessionManager);

	/**
	 * 启动
	 */
	void start();
	void start(int port);
	void start(String host, int port);
}
