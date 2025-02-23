package org.turbo.web.core.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.handler.TurboChannelHandler;
import org.turbo.web.core.http.execetor.HttpDispatcher;
import org.turbo.web.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.web.core.http.execetor.impl.DefaultHttpDispatcher;
import org.turbo.web.core.http.execetor.impl.DefaultHttpExecuteAdaptor;
import org.turbo.web.core.http.handler.DefaultExceptionHandlerMatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerContainer;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionContainer;
import org.turbo.web.core.router.container.RouterContainer;
import org.turbo.web.core.router.matcher.RouterMatcher;
import org.turbo.web.core.router.matcher.impl.DefaultRouterMatcher;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import org.turbo.web.utils.init.ExceptionHandlerContainerInitUtils;
import org.turbo.web.utils.init.RouterContainerInitUtils;

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
    private ServerParamConfig config = new ServerParamConfig();
    private final List<Class<?>> controllerList = new ArrayList<>();
    private final List<Middleware> middlewareList = new ArrayList<>();
    private final List<Class<?>> exceptionHandlerList = new ArrayList<>(1);

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
        // 初始化异常处理器
        ExceptionHandlerContainer exceptionHandlerContainer = ExceptionHandlerContainerInitUtils.initContainer(exceptionHandlerList);
        ExceptionHandlerMatcher exceptionHandlerMatcher = new DefaultExceptionHandlerMatcher(exceptionHandlerContainer);
        log.info("异常处理器初始化成功");
        HttpDispatcher routerDispatcher = createRouterDispatcher();
        HttpExecuteAdaptor httpExecuteAdaptor = new DefaultHttpExecuteAdaptor(routerDispatcher, middlewareList, exceptionHandlerMatcher);
        httpExecuteAdaptor.setShowRequestLog(config.isShowRequestLog());
        // 设置请求封装工具的字符集
        HttpInfoRequestPackageUtils.setCharset(config.getCharset());
        log.info("http适配器初始化成功");
        // 初始化session容器
        initSessionContainer();
        // 设置处理器
        serverBootstrap.childHandler(new TurboChannelHandler(httpExecuteAdaptor, config.getMaxContentLength()));
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

    private void initSessionContainer() {
        SessionContainer.startSentinel(config.getSessionCheckTime(), config.getSessionMaxNotUseTime());
        log.info("session检查哨兵启动成功");
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

    @Override
    public void addController(Class<?> controller) {
        controllerList.add(controller);
    }

    @Override
    public void addController(Class<?>... controllers) {
        controllerList.addAll(List.of(controllers));
    }

    @Override
    public void setConfig(ServerParamConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    @Override
    public void addMiddleware(Middleware middleware) {
        middlewareList.add(middleware);
    }

    @Override
    public void addMiddleware(Middleware... middleware) {
        middlewareList.addAll(List.of(middleware));
    }

    @Override
    public void addExceptionHandler(Class<?> exceptionHandler) {
        exceptionHandlerList.add(exceptionHandler);
    }

    @Override
    public void addExceptionHandler(Class<?>... exceptionHandler) {
        exceptionHandlerList.addAll(List.of(exceptionHandler));
    }
}
