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
import org.turbo.web.core.http.execetor.HttpScheduler;
import org.turbo.web.core.http.execetor.impl.DefaultHttpDispatcher;
import org.turbo.web.core.http.execetor.impl.DefaultHttpScheduler;
import org.turbo.web.core.http.handler.DefaultExceptionHandlerMatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerContainer;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.DefaultSessionManagerProxy;
import org.turbo.web.core.http.session.MemorySessionManager;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.core.init.DefaultJacksonTurboServerInit;
import org.turbo.web.core.init.TurboServerInit;
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
    // 主类字节码对象
    private final Class<?> mainClass;
    // boss事件循环组
    private final NioEventLoopGroup bossGroup;
    // worker事件循环组
    private final NioEventLoopGroup workerGroup;
    // 服务器参数的配置
    private ServerParamConfig config = new ServerParamConfig();
    // 控制器的字节码
    private final List<Class<?>> controllerList = new ArrayList<>();
    // 中间件
    private final List<Middleware> middlewareList = new ArrayList<>();
    // 异常处理器
    private final List<Class<?>> exceptionHandlerList = new ArrayList<>(1);
    // session管理器
    private SessionManager sessionManager = new MemorySessionManager();
    // 是否执行默认的初始化器
    private boolean doDefaultInit = true;
    // 存储默认初始化器的列表
    private final List<TurboServerInit> defaultTurboServerInitList = new ArrayList<>();
    // 用户自定义的初始化器的列表
    private final List<TurboServerInit> customizeTurboServerInitList = new ArrayList<>();

    {
        defaultTurboServerInitList.add(new DefaultJacksonTurboServerInit());
    }

    /**
     * 构造函数
     *
     * @param mainClass       主类
     * @param workerThreadNum 工作线程数
     */
    public DefaultTurboServer(Class<?> mainClass,int workerThreadNum) {
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
        // 初始化路由分发器
        HttpDispatcher routerDispatcher = initHttpRouterDispatcher();
        // 初始化异常处理器
        ExceptionHandlerMatcher exceptionHandlerMatcher = initExceptionHandler();
        // 初始化session管理器代理
        SessionManagerProxy sessionManagerProxy = initSessionManagerProxy();
        // 初始化http请求适配器
        HttpScheduler httpScheduler = initHttpScheduler(routerDispatcher, sessionManagerProxy, exceptionHandlerMatcher);
        // 设置请求封装工具的字符集
        HttpInfoRequestPackageUtils.setCharset(config.getCharset());
        // 设置处理器
        serverBootstrap.childHandler(new TurboChannelHandler(httpScheduler, config.getMaxContentLength()));
    }

    /**
     * 初始化异常处理器
     *
     * @return 异常处理器
     */
    private ExceptionHandlerMatcher initExceptionHandler() {
        ExceptionHandlerContainer container = ExceptionHandlerContainerInitUtils.initContainer(exceptionHandlerList);
        ExceptionHandlerMatcher matcher = new DefaultExceptionHandlerMatcher(container);
        log.info("异常处理器初始化成功");
        return matcher;
    }

    /**
     * 初始化路由分发器
     *
     * @return 路由分发器
     */
    private HttpDispatcher initHttpRouterDispatcher() {
        // 初始化路由定义
        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllerList);
        log.info("http分发器初始化成功");
        // 创建路由匹配器
        RouterMatcher routerMatcher = new DefaultRouterMatcher(routerContainer);
        // 创建http请求分发器
        return new DefaultHttpDispatcher(routerMatcher);
    }

    /**
     * 初始化session容器
     */
    private SessionManagerProxy initSessionManagerProxy() {
        // 创建session管理器代理
        return new DefaultSessionManagerProxy(sessionManager, config);
    }

    /**
     * 初始化http请求调度器
     *
     * @param dispatcher       路由分发器
     * @param matcher          异常处理器匹配器
     * @return                 http请求调度器
     */
    private HttpScheduler initHttpScheduler(
        HttpDispatcher dispatcher,
        SessionManagerProxy sessionManagerProxy,
        ExceptionHandlerMatcher matcher
    ) {
        DefaultHttpScheduler scheduler = new DefaultHttpScheduler(
            dispatcher,
            sessionManagerProxy,
            mainClass,
            middlewareList,
            matcher,
            config
        );
        scheduler.setShowRequestLog(config.isShowRequestLog());
        log.info("http调度器初始化成功");
        return scheduler;
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
        long start = System.currentTimeMillis();
        List<TurboServerInit> turboServerInitList = new ArrayList<>();
        // 判断是否需要加载默认初始化
        if (doDefaultInit) {
            // 将默认初始化加入初始化列表
            turboServerInitList.addAll(defaultTurboServerInitList);
        }
        // 添加自定义初始化
        turboServerInitList.addAll(customizeTurboServerInitList);
        // 执行前置初始化方法
        for (TurboServerInit turboServerInit : turboServerInitList) {
            turboServerInit.beforeTurboServerInit(serverBootstrap);
        }
        log.info("服务器init前置初始化执行结束");
        init();
        // 执行后置初始化方法
        for (TurboServerInit turboServerInit : turboServerInitList) {
            turboServerInit.afterTurboServerInit(serverBootstrap);
        }
        log.info("服务器init后置初始化执行结束");
        // 启动服务器
        ChannelFuture channelFuture = serverBootstrap.bind(port);
        // 处理监听事件
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                long end = System.currentTimeMillis();
                log.info("服务器启动成功，port:{}, 耗时:{}s", port, (end - start) / 1000.00);
                for (TurboServerInit turboServerInit : turboServerInitList) {
                    turboServerInit.afterTurboServerStart();
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
    public void addMiddleware(Middleware... middleware) {
        middlewareList.addAll(List.of(middleware));
    }


    @Override
    public void addExceptionHandler(Class<?>... exceptionHandler) {
        exceptionHandlerList.addAll(List.of(exceptionHandler));
    }

    @Override
    public void addTurboServerInit(TurboServerInit... turboServerInits) {
        this.customizeTurboServerInitList.addAll(List.of(turboServerInits));
    }

    @Override
    public void doDefaultTurboInit(boolean flag) {
        this.doDefaultInit = flag;
    }

    @Override
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
}
