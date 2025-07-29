package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.core.handler.ChannelHandlerFactory;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherBuilder;
import top.turboweb.core.initializer.factory.HttpSchedulerInitBuilder;
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
	 * 添加前置处理器
	 *
	 * @param channelHandlerFactory 前置处理器工厂
	 * @return 当前实例
	 */
	TurboWebServer addNettyFrontHandler(ChannelHandlerFactory channelHandlerFactory);

	/**
	 * 添加后置处理器
	 *
	 * @param channelHandlerFactory 后置处理器工厂
	 * @return 当前实例
	 */
	TurboWebServer addNettyBackHandler(ChannelHandlerFactory channelHandlerFactory);

	/**
	 * 配置服务器参数
	 *
	 * @param consumer 配置
	 * @return 当前实例
	 */
	TurboWebServer configServer(Consumer<HttpServerConfig> consumer);

	/**
	 * 替换服务器参数
	 *
	 * @param httpServerConfig 参数
	 * @return 当前实例
	 */
	TurboWebServer replaceServerConfig(HttpServerConfig httpServerConfig);

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
