package org.turbo.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.handler.TurboChannelHandler;

/**
 * 默认服务器实现类
 */
public class DefaultTurboServer implements TurboServer{

    private final Logger log = LoggerFactory.getLogger(DefaultTurboServer.class);
    private final ServerBootstrap serverBootstrap;
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private int maxContentLength = 1024 * 1024 * 10;

    /**
     * 构造方法
     *
     * @param workerThreadNum 哨兵线程的数量
     */
    public DefaultTurboServer(int workerThreadNum) {
        serverBootstrap = new ServerBootstrap();
        bossGroup = new NioEventLoopGroup(1);
        if (workerThreadNum <= 0) {
            // 设置默认线程数
            int cpuNum = Runtime.getRuntime().availableProcessors();
            if (cpuNum > 1) {
                workerThreadNum = cpuNum - 1;
            } else {
                workerThreadNum = 1;
            }
        }
        workerGroup = new NioEventLoopGroup(workerThreadNum);
    }

    private void init() {
        // 设置线程组
        serverBootstrap.group(bossGroup, workerGroup);
        // 设置管道
        serverBootstrap.channel(NioServerSocketChannel.class);
        // 设置处理器
        serverBootstrap.childHandler(new TurboChannelHandler(maxContentLength));
    }

    /**
     * 设置最大内容长度
     *
     * @param maxContentLength 最大内容长度
     */
    @Override
    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    /**
     * 启动服务器
     */
    @Override
    public void start() {
        start(8080);
    }

    /**
     * 启动服务器
     * @param port 端口
     */
    @Override
    public void start(int port) {
        init();
        // 启动服务器
        ChannelFuture channelFuture = serverBootstrap.bind(port);
        // 处理监听事件
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                log.info("服务器启动成功，port:{}", port);
            } else {
                log.error("服务器启动失败", future.cause());
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });
    }
}
