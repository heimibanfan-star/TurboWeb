package top.turboweb.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.bootstrap.ServerBootstrapConfig;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.function.Consumer;

/**
 * netty服务器的核心
 */
public class CoreNettyServer {

	private final ServerBootstrap serverBootstrap = new ServerBootstrap();
	private final EventLoopGroup workers;

	public CoreNettyServer(int ioThreadNum) {
		if (ioThreadNum <= 0) {
			ioThreadNum = 1;
		}
		workers = new NioEventLoopGroup(ioThreadNum);
		serverBootstrap.group(
			new NioEventLoopGroup(1),
			workers
		);
		serverBootstrap.channel(NioServerSocketChannel.class);
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
		serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
			@Override
			protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
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
}
