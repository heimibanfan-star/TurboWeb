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
 * TurboWebServer实现类
 */
public class BootStrapTurboWebServer extends CoreTurboWebServer implements TurboWebServer {


    private static final Logger log = LoggerFactory.getLogger(BootStrapTurboWebServer.class);
    private HttpServerConfig serverConfig = new HttpServerConfig();
    private final HttpSchedulerInitFactory httpSchedulerInitFactory;
    private final HttpProtocolDispatcherInitFactory httpProtocolDispatcherInitFactory;
    private final CommonSourceInitializer commonSourceInitializer;
    private final List<TurboWebListener> defaultListeners = new ArrayList<>(1);
    private final List<TurboWebListener> customListeners = new ArrayList<>(1);
    private boolean executeDefaultListener = true;

    {
        httpSchedulerInitFactory = new HttpSchedulerInitFactory(this);
        httpProtocolDispatcherInitFactory = new HttpProtocolDispatcherInitFactory(this);
        commonSourceInitializer = new DefaultCommonSourceInitializer();
    }

    public BootStrapTurboWebServer() {
        this(0, 0);
    }
    
    public BootStrapTurboWebServer(int ioThreadNum) {
        this(ioThreadNum, 0);
    }

    public BootStrapTurboWebServer(int ioThreadNum, int zeroCopyThreadNum) {
        super(ioThreadNum, zeroCopyThreadNum);
    }

    @Override
    public HttpProtocolDispatcherBuilder protocol() {
        return this.httpProtocolDispatcherInitFactory;
    }

    @Override
    public HttpSchedulerInitBuilder http() {
        return this.httpSchedulerInitFactory;
    }

    @Override
    public TurboWebServer addNettyFrontHandler(ChannelHandlerFactory channelHandlerFactory) {
        super.addFrontHandler(channelHandlerFactory);
        return this;
    }

    @Override
    public TurboWebServer addNettyBackHandler(ChannelHandlerFactory channelHandlerFactory) {
        super.addBackHandler(channelHandlerFactory);
        return this;
    }

    @Override
    public TurboWebServer configServer(Consumer<HttpServerConfig> consumer) {
        Objects.requireNonNull(consumer, "consumer can not be null");
        consumer.accept(this.serverConfig);
        return this;
    }

    @Override
    public TurboWebServer replaceServerConfig(HttpServerConfig httpServerConfig) {
        Objects.requireNonNull(httpServerConfig, "httpServerConfig can not be null");
        this.serverConfig = httpServerConfig;
        return this;
    }



    @Override
    public TurboWebServer executeDefaultListener(boolean flag) {
        this.executeDefaultListener = flag;
        return this;
    }

    @Override
    public TurboWebServer listeners(TurboWebListener... listeners) {
        customListeners.addAll(List.of(listeners));
        return this;
    }

    @Override
    public TurboWebServer gatewayHandler(GatewayChannelHandler handler) {
        setGatewayChannelHandler(handler);
        return this;
    }

    @Override
    public ChannelFuture start() {
        return start(8080);
    }

    @Override
    public ChannelFuture start(int port) {
        return start("0.0.0.0", port);
    }

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

    @Override
    public void shutdown() {
        super.shutdown();
    }

    /**
     * 初始化
     */
    private void init() {
        // 初始化公共资源
        commonSourceInitializer.init(serverConfig);
        // 创建http调度器
        HttpScheduler httpScheduler = httpSchedulerInitFactory.createHttpScheduler(serverConfig);
        // 创建http协议分发器
        HttpProtocolDispatcher httpProtocolDispatcher = httpProtocolDispatcherInitFactory.createDispatcher(httpScheduler, workers());
        initPipeline(httpProtocolDispatcher, serverConfig.getMaxContentLength(), serverConfig.getCpuNum(), serverConfig.getMaxConnections(), serverConfig.isSerializePerConnection());
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
