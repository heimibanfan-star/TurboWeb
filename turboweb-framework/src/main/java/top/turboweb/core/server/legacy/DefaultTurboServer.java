package top.turboweb.core.server.legacy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.gateway.Gateway;
import top.turboweb.core.initializer.*;
import top.turboweb.core.initializer.impl.*;
import top.turboweb.core.piplines.TurboChannelHandler;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerProxy;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.core.listener.DefaultJacksonTurboWebListener;
import top.turboweb.core.listener.TurboWebListener;
import top.turboweb.http.request.HttpInfoRequestPackageHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认服务器实现类
 */
@Deprecated
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
    // http客户端的初始化器
    private final HttpClientInitializer httpClientInitializer;
    // boss事件循环组
    private final NioEventLoopGroup bossGroup;
    // worker事件循环组
    private final NioEventLoopGroup workerGroup;
    // 服务器参数的配置
    private ServerParamConfig config = new ServerParamConfig();
    // 是否执行默认的监听器
    private boolean doDefaultInit = true;
    // 存储默认监听器的列表
    private final List<TurboWebListener> defaultTurboWebListenerList = new ArrayList<>();
    // 网关
    private Gateway gateway;
    // 用户自定义的监听器的列表
    private final List<TurboWebListener> customizeTurboWebListenerList = new ArrayList<>();

    {
        middlewareInitializer = new DefaultMiddlewareInitializer();
        exceptionHandlerInitializer = new DefaultExceptionHandlerInitializer();
        sessionManagerProxyInitializer = new DefaultSessionManagerProxyInitializer();
        httpSchedulerInitializer = new DefaultHttpSchedulerInitializer();
        webSocketHandlerInitializer = new DefaultWebSocketHandlerInitializer();
        httpClientInitializer = new DefaultHttpClientInitializer();
        defaultTurboWebListenerList.add(new DefaultJacksonTurboWebListener());
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
        // 启用keep alive
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
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
        // 初始化http客户端
        httpClientInitializer.init(workerGroup);
        // 设置请求封装工具的字符集
        HttpInfoRequestPackageHelper.setCharset(config.getCharset());
        if (gateway != null) {
            log.info("gateway已启用");
        }
        // 设置处理器
        if (webSocketHandlerInitializer.isUse()) {
            serverBootstrap.childHandler(new TurboChannelHandler(
                httpScheduler,
                config.getMaxContentLength(),
                webSocketHandlerInitializer.init() ,
                webSocketHandlerInitializer.getPath(),
                gateway
            ));
        } else {
            serverBootstrap.childHandler(new TurboChannelHandler(
                httpScheduler,
                config.getMaxContentLength(),
                null,
                null,
                gateway
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
        List<TurboWebListener> turboWebListenerList = new ArrayList<>();
        // 判断是否需要加载默认初始化
        if (doDefaultInit) {
            // 将默认初始化加入初始化列表
            turboWebListenerList.addAll(defaultTurboWebListenerList);
        }
        // 添加自定义初始化
        turboWebListenerList.addAll(customizeTurboWebListenerList);
        // 执行前置初始化方法
        for (TurboWebListener turboWebListener : turboWebListenerList) {
            turboWebListener.beforeServerInit();
        }
        log.info("服务器init前置初始化执行结束");
        init();
        // 启动服务器
        ChannelFuture channelFuture = serverBootstrap.bind(port);
        // 处理监听事件
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                long end = System.currentTimeMillis();
                log.info("服务器启动成功，port:{}, 耗时:{}s", port, (end - start) / 1000.00);
                for (TurboWebListener turboWebListener : turboWebListenerList) {
                    turboWebListener.afterServerStart();
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
    public void addTurboServerInit(TurboWebListener... turboWebListeners) {
        this.customizeTurboWebListenerList.addAll(List.of(turboWebListeners));
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

    @Override
    public HttpClientInitializer httpClient() {
        return this.httpClientInitializer;
    }

    @Override
    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }
}
