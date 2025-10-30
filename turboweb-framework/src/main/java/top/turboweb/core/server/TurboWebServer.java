package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.handler.ssl.SslContext;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.core.handler.ChannelHandlerFactory;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherBuilder;
import top.turboweb.core.initializer.factory.HttpSchedulerInitBuilder;
import top.turboweb.core.listener.TurboWebListener;
import top.turboweb.gateway.GatewayChannelHandler;

import java.util.function.Consumer;

/**
 * TurboWebServer
 * <p>
 * TurboWeb 的核心启动接口，定义了整个 Web 服务的生命周期管理规范。
 * 通过该接口，开发者可以：
 * <ul>
 *   <li>配置协议分发器与 HTTP 请求调度器</li>
 *   <li>自定义前置 / 后置 Netty 处理器</li>
 *   <li>配置服务器参数（线程数、连接数、缓冲区大小等）</li>
 *   <li>启用 SSL 安全传输</li>
 *   <li>注册生命周期监听器，实现启动前后回调</li>
 * </ul>
 * <p>
 * 该接口是框架启动流程的总入口，由 {@link BootStrapTurboWebServer} 提供默认实现。
 * <p>
 * 所有方法均为链式调用风格（Fluent API），便于快速构建和启动服务器。
 */
public interface TurboWebServer {

	/**
	 * 获取 HTTP 协议分发器的构建器。
	 * <p>
	 * 用于配置底层的 HTTP 协议解析、数据包编解码等功能。
	 *
	 * @return {@link HttpProtocolDispatcherBuilder} 协议分发器构建器
	 */
	HttpProtocolDispatcherBuilder protocol();

	/**
	 * 获取 HTTP 调度器初始化构建器。
	 * <p>
	 * 用于创建 {@link top.turboweb.http.scheduler.HttpScheduler}，
	 * 即 HTTP 请求在业务线程池中的调度逻辑。
	 * 可在此阶段注册请求处理器、序列化方式或负载策略。
	 *
	 * @return {@link HttpSchedulerInitBuilder} HTTP 调度器构建器
	 */
	HttpSchedulerInitBuilder http();

	/**
	 * 在TurboWeb默认的协议处理器之前注册
	 * <p>
	 * 前置处理器会在请求进入框架协议层之前执行，
	 * 适合用于 SSL 握手、IP 黑名单过滤、连接统计等。
	 * <p>
	 * 若使用 {@link ChannelHandlerFactory} 传入工厂形式，
	 * 可以灵活控制 Handler 的生命周期（共享或独占）。
	 *
	 * @param channelHandlerFactory 前置处理器工厂
	 * @return 当前 {@link TurboWebServer} 实例，便于链式调用
	 */
	TurboWebServer addNettyFrontHandler(ChannelHandlerFactory channelHandlerFactory);

	/**
	 * 在TurboWeb默认的协议处理器之后注册
	 *
	 * @param channelHandlerFactory 后置处理器工厂
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer addNettyBackHandler(ChannelHandlerFactory channelHandlerFactory);

	/**
	 * 使用 Lambda 方式配置服务器参数。
	 *
	 * @param consumer 配置函数式接口
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer configServer(Consumer<HttpServerConfig> consumer);

	/**
	 * 替换整个服务器配置对象。
	 * <p>
	 * 用于直接传入自定义的 {@link HttpServerConfig} 实例，
	 * 通常用于从外部加载的配置文件或统一配置中心中读取的配置。
	 *
	 * @param httpServerConfig 完整的服务器配置对象
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer replaceServerConfig(HttpServerConfig httpServerConfig);

	/**
	 * 是否执行默认的监听器。
	 * <p>
	 * 默认监听器包括启动前 / 启动后的一些框架内部初始化回调。
	 * 开发者可通过传入 <code>false</code> 禁用默认监听器，
	 * 以完全控制服务启动流程。
	 *
	 * @param flag 是否执行默认监听器
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer executeDefaultListener(boolean flag);

	/**
	 * 注册自定义服务器监听器。
	 * 适合用于组件初始化、资源加载、动态配置刷新等操作。
	 *
	 * @param listeners 自定义监听器
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer listeners(TurboWebListener... listeners);

	/**
	 * 注册网关层处理器。
	 * <p>
	 * Gateway 处理器允许开发者在框架最外层接入自定义逻辑，
	 * 例如流量路由、请求过滤、权限校验等。
	 * 适用于构建 API 网关或多协议接入层。
	 *
	 * @param handler {@link GatewayChannelHandler} 实例
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer gatewayHandler(GatewayChannelHandler handler);

	/**
	 * 启用 SSL / TLS 加密。
	 * <p>
	 * 传入由 Netty 构建的 {@link SslContext}，
	 * 服务器启动后将在 Netty 管道中自动添加 {@code SslHandler}。
	 * <p>
	 * 示例：
	 * <pre>
	 * SslContext sslCtx = SslContextBuilder
	 *     .forServer(certFile, keyFile)
	 *     .build();
	 * server.ssl(sslCtx);
	 * </pre>
	 *
	 * @param sslContext SSL 上下文对象
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer ssl(SslContext sslContext);

	/**
	 * 启用 HTTP2 支持。
	 * <p>
	 * 启用 HTTP2 后，服务器将使用 HTTP2 协议进行通信。
	 *
	 * @return 当前 {@link TurboWebServer} 实例
	 */
	TurboWebServer enableHttp2();

	/**
	 * 启动服务器（使用默认端口 8080）。
	 * <p>
	 * 启动过程是异步的，返回 {@link ChannelFuture} 可用于监听结果。
	 *
	 * @return Netty 异步 {@link ChannelFuture} 对象
	 */
	ChannelFuture start();
	ChannelFuture start(int port);
	ChannelFuture start(String host, int port);

	/**
	 * 停止
	 */
	void shutdown();
}
