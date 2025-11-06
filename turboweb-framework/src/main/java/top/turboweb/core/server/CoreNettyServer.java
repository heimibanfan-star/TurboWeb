package top.turboweb.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.bootstrap.ServerBootstrapConfig;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.channel.TurboWebNioServerSocketChannel;
import top.turboweb.core.channel.TurboWebNioSocketChannel;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * <p><b>CoreNettyServer</b> 是 TurboWeb 框架的底层核心服务器引导器，负责构建并启动基于 Netty 的高性能 HTTP 服务端。</p>
 *
 * <p>该类封装了 {@link ServerBootstrap} 的初始化逻辑，包括：</p>
 * <ul>
 *     <li>主从线程组（boss/workers）的创建与生命周期管理</li>
 *     <li>基于零拷贝的独立线程池 {@link ThreadPoolExecutor} 管理</li>
 *     <li>自定义 {@link ChannelFactory} 支持 TurboWeb 特有的 Channel 类型</li>
 *     <li>统一的 pipeline 初始化入口</li>
 * </ul>
 *
 * <p>通过该类，框架上层可以灵活配置 Netty 参数、注册管道初始化逻辑，并以线程安全的方式启动或关闭服务器。</p>
 *
 */
public class CoreNettyServer {

	private static final Logger log = LoggerFactory.getLogger(CoreNettyServer.class);

	/**
	 * Netty 服务端引导核心组件。
	 * <p>负责协调事件循环、通道工厂与管道配置。</p>
	 */
	private final ServerBootstrap serverBootstrap = new ServerBootstrap();

	/**
	 * Boss 线程组。
	 * <p>用于接收客户端连接请求（默认仅一个线程即可应对高并发连接接入）。</p>
	 */
	private final EventLoopGroup boss = new NioEventLoopGroup(1);

	/**
	 * Worker 线程组。
	 * <p>负责处理 I/O 读写、事件调度、业务逻辑分发。</p>
	 */
	private final EventLoopGroup workers;

	private final int zeroCopyThreadNum;

	private final ServerChannel serverChannel;

	/**
	 * 自定义零拷贝线程工厂。
	 * <p>用于命名线程并确保后台守护进程模式。</p>
	 */
	private static class ZeroCopyThreadFactory implements ThreadFactory {

		private final AtomicLong count = new AtomicLong();

		@Override
		public Thread newThread(Runnable r) {
			count.compareAndSet(Long.MAX_VALUE, 0);
			Thread thread = new Thread(r, "zero-copy-thread-" + count.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		}
	}

	/**
	 * 构造方法。
	 *
	 * @param ioThreadNum        I/O 工作线程数（建议与 CPU 核心数接近）
	 * @param zeroCopyThreadNum  零拷贝任务线程数（建议为 CPU 核心数的 2 倍）
	 */
	public CoreNettyServer(int ioThreadNum, int zeroCopyThreadNum) {
		this.workers = new NioEventLoopGroup(ioThreadNum);
		serverBootstrap.group(
			boss,
			workers
		);
		this.zeroCopyThreadNum = zeroCopyThreadNum;
		this.serverChannel = null;
	}

	public CoreNettyServer(ServerChannel serverChannel, EventLoopGroup boss, EventLoopGroup workers) {
		Objects.requireNonNull(serverChannel, "serverChannel cannot be null");
		Objects.requireNonNull(boss, "boss cannot be null");
		Objects.requireNonNull(workers, "workers cannot be null");
		this.zeroCopyThreadNum = 0;
		this.serverChannel = serverChannel;
		this.workers = workers;
		// 设置线程组
		serverBootstrap.group(boss, workers);
	}

	/**
	 * 设置服务端通道选项（适用于父通道）。
	 *
	 * @param option 通道选项
	 * @param value  参数值
	 * @param <T>    参数类型
	 */
	public <T> void option(ChannelOption<T> option, T value) {
		serverBootstrap.option(option, value);
	}

	/**
	 * 设置子通道选项（适用于客户端连接通道）。
	 *
	 * @param option 通道选项
	 * @param value  参数值
	 * @param <T>    参数类型
	 */
	public <T> void childOption(ChannelOption<T> option, T value) {
		serverBootstrap.childOption(option, value);
	}

	/**
	 * 注册子通道（每个客户端连接）的 pipeline 初始化逻辑。
	 *
	 * @param ssl 是否启用 ssl
	 * @param consumer 管道初始化逻辑（用于配置业务 Handler 链）
	 */
	public void childChannelInitPipeline(boolean ssl, Consumer<Channel> consumer) {
		if (ssl) {
			this.initChannel(new NioServerSocketChannel(), consumer);
		}
		else {
			// 创建专门用于零拷贝的线程池
			ThreadPoolExecutor zeroCopyExecutor = new ThreadPoolExecutor(
					this.zeroCopyThreadNum,
					this.zeroCopyThreadNum,
					60,
					TimeUnit.SECONDS,
					new LinkedBlockingQueue<>(),
					new ZeroCopyThreadFactory()
			);

			// 允许核心线程过期
			zeroCopyExecutor.allowCoreThreadTimeOut(true);
			// 设置专用的零拷贝增强通道
			this.initChannel(new TurboWebNioServerSocketChannel(zeroCopyExecutor), consumer);
		}
	}

	/**
	 * 注册子通道（每个客户端连接）的 pipeline 逻辑。
	 *
	 * @param serverChannel 专有通道类型
	 * @param consumer 管道初始化逻辑（用于配置业务 Handler 链）
	 */
	private void initChannel(ServerChannel serverChannel, Consumer<Channel> consumer) {
		// 判断构造时是否指定了channel类型
		if (this.serverChannel != null) {
			serverBootstrap.channelFactory(() -> this.serverChannel);
		} else {
			serverBootstrap.channelFactory(() -> serverChannel);
		}
		serverBootstrap.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				consumer.accept(ch);
			}
		});
	}

	/**
	 * 获取当前 {@link ServerBootstrap} 的运行配置。
	 *
	 * @return Netty 服务端引导配置
	 */
	public ServerBootstrapConfig config() {
		return serverBootstrap.config();
	}

	/**
	 * 暴露底层的 {@link ServerBootstrap} 对象。
	 * <p>用于高级定制（例如手动注册事件监听器、TCP 参数调整等）。</p>
	 *
	 * @return ServerBootstrap 实例
	 */
	public ServerBootstrap exportCore() {
		return serverBootstrap;
	}

	/**
	 * 启动服务器。
	 *
	 * @param host 监听主机地址
	 * @param port 监听端口
	 * @return 启动结果的异步 {@link ChannelFuture}
	 */
	public ChannelFuture start(String host, int port) {
		return serverBootstrap.bind(host, port);
	}

	/**
	 * 获取 I/O 工作线程组。
	 *
	 * @return 工作线程组
	 */
	public EventLoopGroup workers() {
		return workers;
	}

	/**
	 * 优雅关闭服务器。
	 * <p>包括 boss 与 worker 线程组的优雅释放。</p>
	 */
	public void shutdown() {
		// 关闭boss线程
		boss.shutdownGracefully().addListener(
				future -> {
					if (future.isSuccess()) {
						// 关闭工作线程组
						workers.shutdownGracefully().addListener(f -> {
							if (f.isSuccess()) {
								log.info("server is stop success");
							} else {
								log.error("worker thread shutdown err");
							}
						});
					} else {
						log.error("boss thread shutdown err");
					}
				}
		);
	}
}
