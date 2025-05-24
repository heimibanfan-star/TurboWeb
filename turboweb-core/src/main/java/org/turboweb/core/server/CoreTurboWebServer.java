package org.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.turboweb.core.dispatch.HttpProtocolDispatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * turboWeb的核心
 */
public class CoreTurboWebServer {

	private final CoreNettyServer coreNettyServer;
	private final List<ChannelHandler> frontHandlers = new ArrayList<>();
	private final List<ChannelHandler> backHandlers = new ArrayList<>();

	public CoreTurboWebServer(int ioThreadNum) {
		this.coreNettyServer = new CoreNettyServer(ioThreadNum);
		coreNettyServer.childOption(ChannelOption.SO_KEEPALIVE, true);
	}

	/**
	 * 添加前端处理器
	 *
	 * @param channelHandler 前端处理器
	 */
	public void addFrontHandler(ChannelHandler channelHandler) {
		frontHandlers.add(channelHandler);
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
	 * @param channelHandler 后端处理器
	 */
	public void addBackHandler(ChannelHandler channelHandler) {
		backHandlers.add(channelHandler);
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
	protected final void initPipeline(HttpProtocolDispatcher dispatcherHandler, int maxContentLen) {
		coreNettyServer.childChannelInitPipeline(pipeline -> {
			for (ChannelHandler channelHandler : frontHandlers) {
				pipeline.addLast(channelHandler);
			}
			pipeline.addLast(new HttpServerCodec());
			pipeline.addLast(new HttpObjectAggregator(maxContentLen));
			pipeline.addLast(new ChunkedWriteHandler());
			pipeline.addLast(dispatcherHandler);
			for (ChannelHandler channelHandler : backHandlers) {
				pipeline.addLast(channelHandler);
			}
		});
	}
}
