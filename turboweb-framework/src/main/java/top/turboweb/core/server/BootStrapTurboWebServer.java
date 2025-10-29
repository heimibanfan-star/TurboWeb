package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.core.handler.ChannelHandlerFactory;
import top.turboweb.core.initializer.CommonSourceInitializer;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherBuilder;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherInitFactory;
import top.turboweb.core.initializer.factory.HttpSchedulerInitBuilder;
import top.turboweb.core.initializer.factory.HttpSchedulerInitFactory;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.core.initializer.impl.DefaultCommonSourceInitializer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.core.listener.TurboWebListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p><b>BootStrapTurboWebServer</b> 是 TurboWeb 框架的核心启动实现类。</p>
 *
 * <p>该类继承 {@link CoreTurboWebServer}，提供了完整的 HTTP 服务启动流程，包括：</p>
 * <ul>
 *     <li>加载与替换 {@link HttpServerConfig} 服务器配置</li>
 *     <li>初始化公共资源与线程池</li>
 *     <li>构建 {@link HttpScheduler}（调度器）与 {@link HttpProtocolDispatcher}（协议分发器）</li>
 *     <li>注册自定义或默认 {@link TurboWebListener} 启动监听器</li>
 *     <li>执行可选的网关代理 {@link GatewayChannelHandler}</li>
 *     <li>启动 Netty 服务并输出启动 Banner</li>
 * </ul>
 *
 * <p>该类是 TurboWeb 启动入口的核心封装，可通过 {@link #create()} 快速创建并运行服务实例。</p>
 *
 */
public class BootStrapTurboWebServer extends CoreTurboWebServer {


    private static final Logger log = LoggerFactory.getLogger(BootStrapTurboWebServer.class);

    /**
     * HTTP 服务器配置对象。
     * <p>包含端口号、线程数量、最大内容长度、连接数限制等参数。</p>
     */
    private HttpServerConfig serverConfig = new HttpServerConfig();

    /**
     * HTTP 调度器初始化工厂。
     * <p>用于构建 {@link HttpScheduler}，负责请求分发与业务线程调度。</p>
     */
    private final HttpSchedulerInitFactory httpSchedulerInitFactory;

    /**
     * HTTP 协议分发器初始化工厂。
     * <p>用于创建 {@link HttpProtocolDispatcher}，实现 HTTP 协议层的数据解码与分派。</p>
     */
    private final HttpProtocolDispatcherInitFactory httpProtocolDispatcherInitFactory;

    /**
     * 公共资源初始化器。
     * <p>负责初始化系统级公共组件（如线程池、内存池、监控模块等）。</p>
     */
    private final CommonSourceInitializer commonSourceInitializer;

    /**
     * 框架内置的默认监听器列表。
     * <p>仅在启用 {@link #executeDefaultListener} 时执行。</p>
     */
    private final List<TurboWebListener> defaultListeners = new ArrayList<>(1);

    /**
     * 用户自定义的监听器列表。
     * <p>用于注册自定义事件钩子，如日志、监控、配置加载等。</p>
     */
    private final List<TurboWebListener> customListeners = new ArrayList<>(1);

    /**
     * 是否执行框架内置监听器。
     * <p>默认为 {@code true}，可通过 {@link #executeDefaultListener(boolean)} 关闭。</p>
     */
    private boolean executeDefaultListener = true;


    {
        httpSchedulerInitFactory = new HttpSchedulerInitFactory(this);
        httpProtocolDispatcherInitFactory = new HttpProtocolDispatcherInitFactory(this);
        commonSourceInitializer = new DefaultCommonSourceInitializer();
    }

    /**
     * 使用默认线程参数创建 TurboWeb 服务器。
     */
    public BootStrapTurboWebServer() {
        this(0, 0);
    }

    /**
     * 使用指定 I/O 线程数创建 TurboWeb 服务器。
     *
     * @param ioThreadNum I/O 线程数量
     */
    public BootStrapTurboWebServer(int ioThreadNum) {
        this(ioThreadNum, 0);
    }

    /**
     * 完整构造方法。
     *
     * @param ioThreadNum       I/O 线程数（Netty Worker Group）
     * @param zeroCopyThreadNum 零拷贝线程数（文件传输与高性能 I/O）
     */
    public BootStrapTurboWebServer(int ioThreadNum, int zeroCopyThreadNum) {
        super(ioThreadNum, zeroCopyThreadNum);
    }

    /**
     * 获取 HTTP 协议分发器构建器。
     *
     * @return {@link HttpProtocolDispatcherBuilder}
     */
    @Override
    public HttpProtocolDispatcherBuilder protocol() {
        return this.httpProtocolDispatcherInitFactory;
    }

    /**
     * 获取 HTTP 调度器构建器。
     *
     * @return {@link HttpSchedulerInitBuilder}
     */
    @Override
    public HttpSchedulerInitBuilder http() {
        return this.httpSchedulerInitFactory;
    }

    /**
     * 配置服务器参数。
     *
     * @param consumer 配置操作函数
     * @return 当前服务器实例
     */
    @Override
    public TurboWebServer configServer(Consumer<HttpServerConfig> consumer) {
        Objects.requireNonNull(consumer, "consumer can not be null");
        consumer.accept(this.serverConfig);
        return this;
    }

    /**
     * 替换整个 HTTP 服务器配置对象。
     *
     * @param httpServerConfig 新的配置实例
     * @return 当前服务器实例
     */
    @Override
    public TurboWebServer replaceServerConfig(HttpServerConfig httpServerConfig) {
        Objects.requireNonNull(httpServerConfig, "httpServerConfig can not be null");
        this.serverConfig = httpServerConfig;
        return this;
    }

    /**
     * 控制是否执行框架内置监听器。
     *
     * @param flag 是否执行
     * @return 当前服务器实例
     */
    @Override
    public TurboWebServer executeDefaultListener(boolean flag) {
        this.executeDefaultListener = flag;
        return this;
    }

    /**
     * 注册自定义监听器。
     *
     * @param listeners 监听器实例列表
     * @return 当前服务器实例
     */
    @Override
    public TurboWebServer listeners(TurboWebListener... listeners) {
        customListeners.addAll(List.of(listeners));
        return this;
    }

    /**
     * 注册网关处理器。
     * <p>用于在请求转发或微服务代理场景下接入 Gateway 模块。</p>
     *
     * @param handler 网关处理器
     * @return 当前服务器实例
     */
    @Override
    public TurboWebServer gatewayHandler(GatewayChannelHandler handler) {
        setGatewayChannelHandler(handler);
        return this;
    }

    /**
     * 启动 HTTP 服务，使用默认端口 8080。
     *
     * @return 启动结果 {@link ChannelFuture}
     */
    @Override
    public ChannelFuture start() {
        return start(8080);
    }

    /**
     * 启动 HTTP 服务。
     *
     * @param port 监听端口
     * @return 启动结果 {@link ChannelFuture}
     */
    @Override
    public ChannelFuture start(int port) {
        return start("0.0.0.0", port);
    }

    /**
     * 启动 HTTP 服务。
     *
     * @param host 主机地址
     * @param port 监听端口
     * @return 启动结果 {@link ChannelFuture}
     */
    @Override
    public ChannelFuture start(String host, int port) {
        printBanner();
        long start = System.currentTimeMillis();
        executeListenerBeforeInit();
        init();
        ChannelFuture channelFuture = startServer(host, port);
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                executeListenerAfterServerStart();
                long time = System.currentTimeMillis() - start;
                log.info("TurboWebServer start on: http://{}:{}, time: {}ms", host, port, time);
            } else {
                super.shutdown();
                log.error("TurboWebServer start failed: {}\n", future.cause().getMessage(), future.cause());
            }

        });
        return channelFuture;
    }

    /**
     * 初始化核心组件。
     * <p>包括公共资源、调度器与协议分发器的构建与注册。</p>
     */
    private void init() {
        // 初始化公共资源
        commonSourceInitializer.init(serverConfig);
        // 创建http调度器
        HttpScheduler httpScheduler = httpSchedulerInitFactory.createHttpScheduler(serverConfig);
        // 创建http协议分发器
        HttpProtocolDispatcher httpProtocolDispatcher = httpProtocolDispatcherInitFactory.createDispatcher(httpScheduler, workers());
        super.initPipeline(httpProtocolDispatcher, serverConfig.getMaxContentLength(), serverConfig.getCpuNum(), serverConfig.getMaxConnections(), serverConfig.isSerializePerConnection());
    }

    public static void printBanner() {
        String banner = """
                 _______         _       __          __  _    \s
                |__   __|       | |      \\ \\        / / | |   \s
                   | |_   _ _ __| |__   __\\ \\  /\\  / /__| |__ \s
                   | | | | | '__| '_ \\ / _ \\ \\/  \\/ / _ \\ '_ \\\s
                   | | |_| | |  | |_) | (_) \\  /\\  /  __/ |_) |
                   |_|\\__,_|_|  |_.__/ \\___/ \\/  \\/ \\___|_.__/\s
                                                              \s
                :: TurboWeb - Lightweight ⚙ Turbocharged ⚡ Web Framework ::
                :: Powered by Virtual Threads + Netty, Built for Speed & Simplicity ::
                """;
        System.out.println(banner);
    }

    /**
     * 执行监听器
     */
    private void executeListenerBeforeInit() {
        if (executeDefaultListener) {
            for (TurboWebListener turboWebListener : defaultListeners) {
                turboWebListener.beforeServerInit();
            }
        }
        for (TurboWebListener turboWebListener : customListeners) {
            turboWebListener.beforeServerInit();
        }
        log.info("TurboWeb初始化前置监听器方法执行完成");
    }

    /**
     * 执行监听器
     */
    private void executeListenerAfterServerStart() {
        if (executeDefaultListener) {
            for (TurboWebListener turboWebListener : defaultListeners) {
                turboWebListener.afterServerStart();
            }
        }
        for (TurboWebListener turboWebListener : customListeners) {
            turboWebListener.afterServerStart();
        }
        log.info("TurboWeb启动后监听器方法执行完成");
    }

    /**
     * 创建TurboWebServer
     *
     * @return TurboWebServer
     */
    public static TurboWebServer create() {
        return new BootStrapTurboWebServer();
    }

    /**
     * 创建TurboWebServer
     *
     * @param ioThreadNum IO线程数
     * @return TurboWebServer
     */
    public static TurboWebServer create(int ioThreadNum) {
        return new BootStrapTurboWebServer(ioThreadNum);
    }
}
