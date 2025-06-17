package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherBuilder;
import top.turboweb.core.initializer.factory.HttpSchedulerInitBuilder;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManager;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.core.listener.TurboWebListener;

import java.util.function.Consumer;

/**
 * turboWeb的核心启动接口
 */
public interface TurboWebServer {

	/**
	 * 配置协议处理
	 *
	 * @return HttpProtocolDispatcherBuilder
	 */
	HttpProtocolDispatcherBuilder protocol();

	/**
	 * 配置http处理
	 *
	 * @return HttpSchedulerInitBuilder
	 */
	HttpSchedulerInitBuilder http();

	/**
	 * 添加配置
	 *
	 * @param consumer 配置
	 * @return 当前实例
	 */
	TurboWebServer config(Consumer<ServerParamConfig> consumer);
	/**
	 * 执行默认的监听器
	 *
	 * @param flag 是否执行
	 * @return 当前实例
	 */
	TurboWebServer executeDefaultListener(boolean flag);

	/**
	 * 添加监听器
	 *
	 * @param listeners 监听器
	 * @return 当前实例
	 */
	TurboWebServer listeners(TurboWebListener... listeners);

	/**
	 * 启动
	 *
	 * @return ChannelFuture 异步对象
	 */
	ChannelFuture start();
	ChannelFuture start(int port);
	ChannelFuture start(String host, int port);
}
