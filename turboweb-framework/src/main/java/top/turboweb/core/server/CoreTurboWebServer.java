package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.core.handler.ConnectLimiter;
import top.turboweb.core.handler.ChannelHandlerFactory;
import top.turboweb.core.handler.RequestSerializerHandler;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.gateway.client.ReactorHttpClientFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TurboWeb 核心服务抽象类。
 *
 * <p>该类封装了 TurboWeb 的服务器核心启动逻辑，包括：
 * <ul>
 *   <li>Netty 服务端的初始化与启动</li>
 *   <li>Pipeline 的动态构建</li>
 *   <li>SSL/TLS 支持（HTTPS）</li>
 *   <li>连接数限制与序列化控制</li>
 *   <li>网关通道集成（可选）</li>
 * </ul>
 *
 * <p>该类通常作为框架底层基类，业务服务应继承该类并实现协议分发逻辑。</p>
 */
public abstract class CoreTurboWebServer implements TurboWebServer {

	/**
	 * 核心的 Netty 服务对象。
	 *
	 * <p>负责底层 ServerBootstrap 的启动、监听、线程池管理及通道配置。
	 * CoreTurboWebServer 基于该对象进行服务的生命周期控制。</p>
	 */
	private final CoreNettyServer coreNettyServer;

	/**
	 * 前置处理器工厂集合。
	 *
	 * <p>用于在 HTTP 解码器之前动态插入自定义的 ChannelHandler，
	 * 常用于请求限流、监控、鉴权、日志等前置逻辑。</p>
	 *
	 * <p>这些工厂会在 pipeline 初始化时按注册顺序添加。</p>
	 */
	private final List<ChannelHandlerFactory> frontHandlerFactories = new ArrayList<>();

	/**
	 * 后置处理器工厂集合。
	 *
	 * <p>用于在请求分发完成后或响应阶段添加自定义逻辑，
	 * 如响应过滤、统计、异常统一处理等。</p>
	 *
	 * <p>这些工厂会在 pipeline 的末尾按注册顺序添加。</p>
	 */
	private final List<ChannelHandlerFactory> backHandlerFactories = new ArrayList<>();

	/**
	 * I/O 线程数量。
	 *
	 * <p>用于创建 Netty 的工作线程组（EventLoopGroup），
	 * 每个线程负责处理若干通道的 I/O 事件。</p>
	 */
	private final int ioThreadNum;

	/**
	 * 网关通道处理器（可选）。
	 *
	 * <p>当 TurboWeb 作为反向代理或网关模式运行时，该字段用于处理
	 * 转发逻辑、上游路由与 Reactor HTTP 客户端的协作。</p>
	 */
	private GatewayChannelHandler gatewayChannelHandler;

	/**
	 * SSL 上下文（可选）。
	 *
	 * <p>如果配置该字段，则会在 pipeline 中自动注入 {@link SslHandler}，
	 * 实现 HTTPS / WSS 等加密通信通道。</p>
	 *
	 * <p>未设置时默认以 HTTP 明文方式运行。</p>
	 */
	private SslContext sslContext;

	/**
	 * 构造核心 TurboWeb 服务器。
	 *
	 * @param ioThreadNum       I/O 线程数量（若 ≤ 0 则默认为 1）
	 * @param zeroCopyThreadNum 零拷贝线程数量（若 ≤ 0 则使用 CPU 核心数 × 2）
	 */
	public CoreTurboWebServer(int ioThreadNum, int zeroCopyThreadNum) {
		if (ioThreadNum <= 0) {
			ioThreadNum = 1;
		}
		if (zeroCopyThreadNum <= 0) {
			zeroCopyThreadNum = Runtime.getRuntime().availableProcessors() * 2;
		}
		this.coreNettyServer = new CoreNettyServer(ioThreadNum, zeroCopyThreadNum);
		coreNettyServer.childOption(ChannelOption.SO_KEEPALIVE, true);
		this.ioThreadNum = ioThreadNum;
	}

	/**
	 * 添加一个前置处理器工厂。
	 *
	 * <p>该工厂创建的 Handler 会在 HTTP 解码器之前执行。</p>
	 *
	 * @param handlerFactory 处理器工厂实例
	 * @return 当前服务器实例（支持链式调用）
	 */
	@Override
	public TurboWebServer addNettyFrontHandler(ChannelHandlerFactory handlerFactory) {
		frontHandlerFactories.add(handlerFactory);
		return this;
	}

	/**
	 * 添加一个后置处理器工厂。
	 *
	 * <p>该工厂创建的 Handler 会在请求分发和响应阶段执行。</p>
	 *
	 * @param handlerFactory 处理器工厂实例
	 * @return 当前服务器实例（支持链式调用）
	 */
	@Override
	public TurboWebServer addNettyBackHandler(ChannelHandlerFactory handlerFactory) {
		backHandlerFactories.add(handlerFactory);
		return this;
	}

	/**
	 * 设置服务端通道参数（ServerBootstrap.option）。
	 *
	 * @param option 通道选项
	 * @param value  对应的值
	 */
	public <T> void option(ChannelOption<T> option, T value) {
		coreNettyServer.option(option, value);
	}

	/**
	 * 设置子通道参数（ServerBootstrap.childOption）。
	 *
	 * @param option 通道选项
	 * @param value  对应的值
	 */
	public <T> void childOption(ChannelOption<T> option, T value) {
		coreNettyServer.childOption(option, value);
	}

	/**
	 * 启动服务器。
	 *
	 * @param host 绑定的主机地址
	 * @param port 绑定的端口
	 * @return 启动结果的 {@link ChannelFuture}
	 */
	protected final ChannelFuture startServer(String host, int port) {
		return coreNettyServer.start(host, port);
	}

	/**
	 * 获取工作线程组。
	 *
	 * @return Netty 的工作线程组
	 */
	protected EventLoopGroup workers() {
		return coreNettyServer.workers();
	}

	/**
	 * 启用 SSL 支持。
	 *
	 * @param sslContext SSL 上下文对象
	 * @return 当前服务器实例（支持链式调用）
	 */
	@Override
	public TurboWebServer ssl(SslContext sslContext) {
		this.sslContext = sslContext;
		return this;
	}

	/**
	 * 初始化管线（Pipeline）。
	 *
	 * <p>该方法定义了每个通道的处理器结构，包括：
	 * <ul>
	 *   <li>连接数限制器 {@link ConnectLimiter}</li>
	 *   <li>SSL/TLS 加密层 {@link SslHandler}</li>
	 *   <li>HTTP 编解码器与聚合器</li>
	 *   <li>可选的网关转发与序列化控制</li>
	 *   <li>核心的协议分发器 {@link HttpProtocolDispatcher}</li>
	 * </ul>
	 *
	 * @param dispatcherHandler HTTP 请求分发器
	 * @param maxContentLen     最大聚合请求体长度
	 * @param nCPU              CPU 核心数
	 * @param maxConnect        最大并发连接数
	 * @param serForPerConn     是否启用每连接序列化
	 */
	protected final void initPipeline(HttpProtocolDispatcher dispatcherHandler, int maxContentLen, int nCPU, int maxConnect, boolean serForPerConn) {
		ConnectLimiter connectLimiter = new ConnectLimiter(maxConnect, ioThreadNum, nCPU);
		coreNettyServer.childChannelInitPipeline(socketChannel -> {
			ChannelPipeline pipeline = socketChannel.pipeline();
			// 添加连接限制器
			pipeline.addFirst(connectLimiter);
			// 判断是否开启ssl
			if (sslContext != null) {
				pipeline.addLast(new SslHandler(sslContext.newEngine(socketChannel.alloc())));
			}
			// 添加前置处理器
			for (ChannelHandlerFactory frontHandlerFactory : frontHandlerFactories) {
				pipeline.addLast(frontHandlerFactory.create());
			}
			pipeline.addLast(new HttpServerCodec());
			pipeline.addLast(new HttpObjectAggregator(maxContentLen));
			// 判断是否开启网关
			if (gatewayChannelHandler != null) {
				pipeline.addLast(gatewayChannelHandler);
			}
			if (serForPerConn) {
				pipeline.addLast(new RequestSerializerHandler());
			}
//			pipeline.addLast(new ChunkedWriteHandler());
			pipeline.addLast(dispatcherHandler);
			for (ChannelHandlerFactory backHandlerFactory : backHandlerFactories) {
				pipeline.addLast(backHandlerFactory.create());
			}
		});
	}

	/**
	 * 设置网关通道处理器。
	 *
	 * <p>该方法会自动初始化 Reactor HTTP 客户端，
	 * 并注入到网关处理器中以支持下游转发。</p>
	 *
	 * @param gatewayChannelHandler 网关处理器实例
	 */
	protected void setGatewayChannelHandler(GatewayChannelHandler gatewayChannelHandler) {
		gatewayChannelHandler.setHttpClient(ReactorHttpClientFactory.createHttpClient(workers(), builder -> builder));
		this.gatewayChannelHandler = gatewayChannelHandler;
	}

	/**
	 * 优雅关闭服务器。
	 *
	 * <p>该方法会释放所有 I/O 资源并关闭工作线程组。</p>
	 */
	@Override
	public void shutdown() {
		coreNettyServer.shutdown();
	}
}
