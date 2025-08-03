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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * netty服务器的核心
 */
public class CoreNettyServer {

	private static final Logger log = LoggerFactory.getLogger(CoreNettyServer.class);
	private final ServerBootstrap serverBootstrap = new ServerBootstrap();
	private final EventLoopGroup boss = new NioEventLoopGroup(1);
	private final EventLoopGroup workers;
	private final ThreadPoolExecutor zeroCopyExecutor;

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

	public CoreNettyServer(int ioThreadNum, int zeroCopyThreadNum) {
		if (ioThreadNum <= 0) {
			ioThreadNum = 1;
		}
		if (zeroCopyThreadNum <= 0) {
			zeroCopyThreadNum = Runtime.getRuntime().availableProcessors() * 2;
		}
		// 创建专门用于零拷贝的线程池
		zeroCopyExecutor = new ThreadPoolExecutor(
				zeroCopyThreadNum,
				zeroCopyThreadNum,
				60,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(),
				new ZeroCopyThreadFactory()
		);
		// 允许核心线程过期
		zeroCopyExecutor.allowCoreThreadTimeOut(true);
		workers = new NioEventLoopGroup(ioThreadNum);
		serverBootstrap.group(
			boss,
			workers
		);
//		serverBootstrap.channel(TurboWebNioServerSocketChannel.class);
		serverBootstrap.channelFactory(new ChannelFactory<TurboWebNioServerSocketChannel>() {
			@Override
			public TurboWebNioServerSocketChannel newChannel() {
				return new TurboWebNioServerSocketChannel(zeroCopyExecutor);
			}
		});
	}

	/**
	 * 设置参数
	 *
	 * @param option   参数
	 * @param value    值
	 * @param <T>      泛型
	 */
	public <T> void option(ChannelOption<T> option, T value) {
		serverBootstrap.option(option, value);
	}

	/**
	 * 设置参数
	 *
	 * @param option   参数
	 * @param value    值
	 * @param <T>      泛型
	 */
	public <T> void childOption(ChannelOption<T> option, T value) {
		serverBootstrap.childOption(option, value);
	}

	/**
	 * 初始化子通道的管道
	 *
	 * @param consumer 函数式接口
	 */
	public void childChannelInitPipeline(Consumer<ChannelPipeline> consumer) {
		serverBootstrap.childHandler(new ChannelInitializer<TurboWebNioSocketChannel>() {
			@Override
			protected void initChannel(TurboWebNioSocketChannel nioSocketChannel) throws Exception {
				consumer.accept(nioSocketChannel.pipeline());
			}
		});
	}

	/**
	 * 获取配置
	 *
	 * @return 配置
	 */
	public ServerBootstrapConfig config() {
		return serverBootstrap.config();
	}

	/**
	 * 暴露核心
	 *
	 * @return 核心
	 */
	public ServerBootstrap exportCore() {
		return serverBootstrap;
	}

	/**
	 * 启动
	 *
	 * @param host 主机
	 * @param port 端口
	 * @return 启动结果
	 */
	public ChannelFuture start(String host, int port) {
		return serverBootstrap.bind(host, port);
	}

	/**
	 * 获取工作线程组
	 *
	 * @return 工作线程组
	 */
	public EventLoopGroup workers() {
		return workers;
	}

	/**
	 * 停止
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
