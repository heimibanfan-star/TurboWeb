package org.turbo.web.core.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.handler.TurboChannelHandler;
import org.turbo.web.core.handler.piplines.WebSocketDispatcherHandler;
import org.turbo.web.core.http.scheduler.HttpScheduler;
import org.turbo.web.core.http.scheduler.impl.LoomThreadHttpScheduler;
import org.turbo.web.core.http.scheduler.impl.ReactiveHttpScheduler;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.DefaultSessionManagerProxy;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.initializer.*;
import org.turbo.web.core.initializer.impl.*;
import org.turbo.web.core.listener.DefaultJacksonTurboServerListener;
import org.turbo.web.core.listener.TurboServerListener;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.exception.TurboWebSocketException;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认服务器实现类
 */
public class DefaultTurboServer implements TurboServer {

    private final Logger log = LoggerFactory.getLogger(DefaultTurboServer.class);
    private final ServerBootstrap serverBootstrap;
    // 主类字节码对象
    private final Class<?> mainClass;
    // 中间件初始化器
    private final MiddlewareInitializer middlewareInitializer;
    // 异常处理器初始化器
    private final ExceptionHandlerInitializer exceptionHandlerInitializer;
    // session管理器初始化器
    private final SessionManagerProxyInitializer sessionManagerProxyInitializer;
    // http调度器初始化器
    private final HttpSchedulerInitializer httpSchedulerInitializer;
    // websocket初始化器
    private final WebSocketHandlerInitializer webSocketHandlerInitializer;
    // boss事件循环组
    private final NioEventLoopGroup bossGroup;
    // worker事件循环组
    private final NioEventLoopGroup workerGroup;
    // 服务器参数的配置
    private ServerParamConfig config = new ServerParamConfig();
    // 是否执行默认的监听器
    private boolean doDefaultInit = true;
    // 存储默认监听器的列表
    private final List<TurboServerListener> defaultTurboServerListenerList = new ArrayList<>();
    // 用户自定义的监听器的列表
    private final List<TurboServerListener> customizeTurboServerListenerList = new ArrayList<>();

    {
        middlewareInitializer = new DefaultMiddlewareInitializer();
        exceptionHandlerInitializer = new DefaultExceptionHandlerInitializer();
        sessionManagerProxyInitializer = new DefaultSessionManagerProxyInitializer();
        httpSchedulerInitializer = new DefaultHttpSchedulerInitializer();
        webSocketHandlerInitializer = new DefaultWebSocketHandlerInitializer();
        defaultTurboServerListenerList.add(new DefaultJacksonTurboServerListener());
    }

    /**
     * 构造函数
     *
     * @param mainClass       主类
     * @param workerThreadNum 工作线程数
     */
    public DefaultTurboServer(Class<?> mainClass, int workerThreadNum) {
        this.mainClass = mainClass;
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

    /**
     * 构造函数
     *
     * @param mainClass 主类
     */
    public DefaultTurboServer(Class<?> mainClass) {
        this(mainClass, 0);
    }

    private void init() {
        // 设置线程组
        serverBootstrap.group(bossGroup, workerGroup);
        // 设置管道
        serverBootstrap.channel(NioServerSocketChannel.class);
        // 初始化异常处理器
        ExceptionHandlerMatcher exceptionHandlerMatcher = exceptionHandlerInitializer.init();
        // 初始化session管理器代理
        SessionManagerProxy sessionManagerProxy = sessionManagerProxyInitializer.init(config);
        // 初始化中间件
        Middleware chainSentinel = middlewareInitializer.init(sessionManagerProxy, mainClass, exceptionHandlerMatcher, config);
        // 初始化http请求适配器
        HttpScheduler httpScheduler = httpSchedulerInitializer.init(sessionManagerProxy, exceptionHandlerMatcher, chainSentinel, config);
        // 设置请求封装工具的字符集
        HttpInfoRequestPackageUtils.setCharset(config.getCharset());
        // 设置处理器
        if (webSocketHandlerInitializer.isUse()) {
            serverBootstrap.childHandler(new TurboChannelHandler(
                httpScheduler,
                config.getMaxContentLength(),
                webSocketHandlerInitializer.init() ,
                webSocketHandlerInitializer.getPath()
            ));
        } else {
            serverBootstrap.childHandler(new TurboChannelHandler(
                httpScheduler,
                config.getMaxContentLength(),
                null,
                null
            ));
        }
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
     *
     * @param port 端口
     */
    @Override
    public void start(int port) {
        long start = System.currentTimeMillis();
        List<TurboServerListener> turboServerListenerList = new ArrayList<>();
        // 判断是否需要加载默认初始化
        if (doDefaultInit) {
            // 将默认初始化加入初始化列表
            turboServerListenerList.addAll(defaultTurboServerListenerList);
        }
        // 添加自定义初始化
        turboServerListenerList.addAll(customizeTurboServerListenerList);
        // 执行前置初始化方法
        for (TurboServerListener turboServerListener : turboServerListenerList) {
            turboServerListener.beforeTurboServerInit(serverBootstrap);
        }
        log.info("服务器init前置初始化执行结束");
        init();
        // 执行后置初始化方法
        for (TurboServerListener turboServerListener : turboServerListenerList) {
            turboServerListener.afterTurboServerInit(serverBootstrap);
        }
        log.info("服务器init后置初始化执行结束");
        // 启动服务器
        ChannelFuture channelFuture = serverBootstrap.bind(port);
        // 处理监听事件
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                long end = System.currentTimeMillis();
                log.info("服务器启动成功，port:{}, 耗时:{}s", port, (end - start) / 1000.00);
                for (TurboServerListener turboServerListener : turboServerListenerList) {
                    turboServerListener.afterTurboServerStart();
                }
                log.info("服务器启动后初始化方法完成");
            } else {
                log.error("服务器启动失败", future.cause());
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });
    }

    @Override
    public void addController(Object... controllers) {
        middlewareInitializer.addController(controllers);
    }

    @Override
    public void setConfig(ServerParamConfig config) {
        if (config != null) {
            this.config = config;
        }
    }


    @Override
    public void addMiddleware(Middleware... middleware) {
        middlewareInitializer.addMiddleware(middleware);
    }


    @Override
    public void addExceptionHandler(Object... exceptionHandler) {
        exceptionHandlerInitializer.addExceptionHandler(exceptionHandler);
    }

    @Override
    public void addTurboServerInit(TurboServerListener... turboServerListeners) {
        this.customizeTurboServerListenerList.addAll(List.of(turboServerListeners));
    }

    @Override
    public void doDefaultTurboInit(boolean flag) {
        this.doDefaultInit = flag;
    }

    @Override
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManagerProxyInitializer.setSessionManager(sessionManager);
    }

    @Override
    public void setIsReactiveServer(boolean flag) {
        httpSchedulerInitializer.isReactive(flag);
    }

    @Override
    public void setWebSocketHandler(String path, WebSocketHandler webSocketHandler) {
        webSocketHandlerInitializer.setWebSocketHandler(path, webSocketHandler);
    }
}
