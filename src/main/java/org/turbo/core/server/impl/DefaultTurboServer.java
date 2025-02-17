package org.turbo.core.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.handler.TurboChannelHandler;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.core.http.execetor.impl.DefaultHttpDispatcher;
import org.turbo.core.http.execetor.impl.DefaultHttpExecuteAdaptor;
import org.turbo.core.router.container.RouterContainer;
import org.turbo.core.router.matcher.RouterMatcher;
import org.turbo.core.router.matcher.impl.DefaultRouterMatcher;
import org.turbo.core.server.TurboServer;
import org.turbo.utils.init.RouterContainerInitUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认服务器实现类
 */
public class DefaultTurboServer implements TurboServer {

    private final Logger log = LoggerFactory.getLogger(DefaultTurboServer.class);
    private final ServerBootstrap serverBootstrap;
    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private int maxContentLength = 1024 * 1024 * 10;
    private final List<Class<?>> controllerList = new ArrayList<>();

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
        HttpDispatcher routerDispatcher = createRouterDispatcher();
        HttpExecuteAdaptor httpExecuteAdaptor = new DefaultHttpExecuteAdaptor(routerDispatcher);
        log.info("http适配器初始化成功");
        // 设置处理器
        serverBootstrap.childHandler(new TurboChannelHandler(httpExecuteAdaptor, maxContentLength));
    }

    /**
     * 初始化路由分发器
     *
     * @return 路由分发器
     */
    private HttpDispatcher createRouterDispatcher() {
        // 初始化路由定义
        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllerList);
        log.info("http分发器初始化成功");
        // 创建路由匹配器
        RouterMatcher routerMatcher = new DefaultRouterMatcher(routerContainer);
        // 创建http请求分发器
        return new DefaultHttpDispatcher(routerMatcher);
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
