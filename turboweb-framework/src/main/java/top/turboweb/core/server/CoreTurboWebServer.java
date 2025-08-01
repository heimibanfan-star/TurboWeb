package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.core.handler.ConnectLimiter;
import top.turboweb.core.handler.ChannelHandlerFactory;
import top.turboweb.core.piplines.RequestSerializerHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * turboWeb的核心
 */
public class CoreTurboWebServer {

	private final CoreNettyServer coreNettyServer;
	private final List<ChannelHandlerFactory> frontHandlerFactories = new ArrayList<>();
	private final List<ChannelHandlerFactory> backHandlerFactories = new ArrayList<>();
	private final int ioThreadNum;

	public CoreTurboWebServer(int ioThreadNum, int zeroCopyThreadNum) {
		this.coreNettyServer = new CoreNettyServer(ioThreadNum, zeroCopyThreadNum);
		coreNettyServer.childOption(ChannelOption.SO_KEEPALIVE, true);
		this.ioThreadNum = ioThreadNum;
	}

	/**
	 * 添加前端处理器
	 *
	 * @param handlerFactory 前端处理器
	 */
	public void addFrontHandler(ChannelHandlerFactory handlerFactory) {
		frontHandlerFactories.add(handlerFactory);
	}

	/**
	 * 添加前端选项
	 *
	 * @param option 选项
	 * @param value 值
	 */
	public <T> void option(ChannelOption<T> option, T value) {
		coreNettyServer.option(option, value);
	}

	/**
	 * 添加后端选项
	 *
	 * @param option 选项
	 * @param value 值
	 */
	public <T> void childOption(ChannelOption<T> option, T value) {
		coreNettyServer.childOption(option, value);
	}

	/**
	 * 添加后端处理器
	 *
	 * @param handlerFactory 后端处理器
	 */
	public void addBackHandler(ChannelHandlerFactory handlerFactory) {
		backHandlerFactories.add(handlerFactory);
	}

	/**
	 * 初始化并启动
	 *
	 * @param host 主机
	 * @param port 端口
	 */
	protected final ChannelFuture startServer(String host, int port) {
		return coreNettyServer.start(host, port);
	}

	/**
	 * 获取工作线程组
	 *
	 * @return 工作线程组
	 */
	protected EventLoopGroup workers() {
		return coreNettyServer.workers();
	}

	/**
	 * 初始化pipeline
	 *
	 * @param dispatcherHandler http工作分发器
	 * @param maxContentLen 最大内容长度
	 */
	protected final void initPipeline(HttpProtocolDispatcher dispatcherHandler, int maxContentLen, int nCPU, int maxConnect, boolean serForPerConn) {
		ConnectLimiter connectLimiter = new ConnectLimiter(maxConnect, ioThreadNum, nCPU);
		coreNettyServer.childChannelInitPipeline(pipeline -> {
			// 添加连接限制器
			pipeline.addFirst(connectLimiter);
			// 添加前置处理器
			for (ChannelHandlerFactory frontHandlerFactory : frontHandlerFactories) {
				pipeline.addLast(frontHandlerFactory.create());
			}
			pipeline.addLast(new HttpServerCodec());
			pipeline.addLast(new HttpObjectAggregator(maxContentLen));
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
}
