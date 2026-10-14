package top.turboweb.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.NonNull;
import top.turboweb.http.server.application.Application;
import top.turboweb.http.server.channel.ServerChannelInitializer;
import top.turboweb.http.server.channel.TurboWebNioServerSocketChannel;
import top.turboweb.http.server.config.ServerConfig;
import top.turboweb.http.server.handler.ConnectLimiterChannelHandler;
import top.turboweb.http.server.manager.ApplicationManager;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TurboWeb http服务器。
 * <p>
 * 构造时只做持有：{@link ApplicationManager} 由本类内部创建并私有持有，不对外暴露，调用方通过
 * {@link #registerApplicationFirst(Application)} / {@link #registerApplicationLast(Application)}
 * 间接注册应用；{@link ServerConfig} 可选，不传时使用 {@code ServerConfig.builder().build()}
 * 的默认配置。构造不创建任何线程与通道资源；boss/worker线程组、服务端通道、连接限流器、管道装配器与bootstrap
 * 等全部资源只在 {@link #start(int)} 首次调用时创建，启用http2未配ssl等校验也在此时
 * （绑定端口前）完成。
 * <ul>
 *     <li>线程模型（数量统一由 {@link ServerConfig} 配置）：1个boss线程 + ioThreadNum个
 *     worker线程（默认1） + zeroCopyThreadNum个零拷贝线程（默认cpu核数*2，守护线程且允许核心
 *     线程超时）；netty 4.2起NioEventLoopGroup已废弃，按官方迁移指引使用NioIoHandler工厂的
 *     MultiThreadIoEventLoopGroup；</li>
 *     <li>通道选择（启动时按配置的sslContext确定）：未启用SSL时使用零拷贝
 *     {@link TurboWebNioServerSocketChannel}，启用SSL时使用普通 {@link NioServerSocketChannel}
 *     （二者互斥，零拷贝线程池由通道关闭逻辑负责释放）；</li>
 *     <li>pipeline装配：服务器自身不做装配，启动时创建连接限流器与 {@link ServerChannelInitializer}
 *     并作为childHandler交给bootstrap，由装配器按 限流 → ssl → 编解码（或http2协议协商）→ 应用
 *     的固定顺序装配每条连接，其中应用部分委托 {@link ApplicationManager#configure(ChannelPipeline)}
 *     追加。</li>
 * </ul>
 * 服务器不做优雅停机回调、生命周期监听等扩展，启动返回Netty的 {@link ChannelFuture}，
 * 调用方可自行 sync 或注册监听。
 *
 * <pre>{@code
 * ServerConfig config = ServerConfig.builder()
 *         .ioThreadNum(4)
 *         .maxConnect(20000)
 *         .build();
 * TurboWebHttpServer server = new TurboWebHttpServer(config)
 *         .registerApplicationFirst(httpRequestApplication)  // 最先装配
 *         .registerApplicationLast(webSocketApplication);    // 最后装配
 * // 不传配置即使用默认配置：new TurboWebHttpServer().registerApplicationLast(app);
 * // 异步启动（此时才创建线程组与通道资源并绑定端口），需要阻塞等待时自行 sync
 * server.start(8080);
 * // 停止（实现AutoCloseable，close会关闭监听通道并优雅停机，也可try-with-resources）
 * server.close();
 * }</pre>
 */
public final class TurboWebHttpServer implements AutoCloseable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(TurboWebHttpServer.class);

    /**
     * 自定义零拷贝线程工厂。
     * <p>用于命名线程并确保后台守护进程模式。</p>
     */
    private static class ZeroCopyThreadFactory implements ThreadFactory {

        private final AtomicLong count = new AtomicLong();

        @Override
        public Thread newThread(@NonNull Runnable r) {
            count.compareAndSet(Long.MAX_VALUE, 0);
            Thread thread = new Thread(r, "zero-copy-thread-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 应用管理器（final持有，由本类内部创建且不对外暴露），应用级处理器在每条新连接建立时
     * 由它按注册顺序装配，调用方经 {@link #registerApplicationFirst(Application)} /
     * {@link #registerApplicationLast(Application)} 间接注册应用；
     * 服务器级传输处理器（限流、ssl、编解码/http2）由 {@link ServerChannelInitializer} 装配
     */
    private final ApplicationManager applicationManager;

    /**
     * 服务器配置（不可变），io线程数、零拷贝线程数与ssl上下文等启动期资源参数取自本配置；
     * 启动时创建的 {@link ServerChannelInitializer} 也持有同一份配置，用于每条连接的装配
     */
    private final ServerConfig serverConfig;

    /**
     * boss线程组（处理accept事件），启动时创建
     */
    private EventLoopGroup bossGroup;

    /**
     * worker线程组（处理io事件），启动时按配置的io线程数创建
     */
    private EventLoopGroup workerGroup;

    /**
     * 服务端通道，启动时按是否启用ssl确定零拷贝通道或普通通道
     */
    private ServerChannel serverChannel;

    /**
     * netty引导对象，启动时装配
     */
    private ServerBootstrap serverBootstrap;

    /**
     * 构造服务器，使用默认的服务器配置（{@code ServerConfig.builder().build()}，各参数取建造者默认值）
     */
    public TurboWebHttpServer() {
        this(ServerConfig.builder().build());
    }

    /**
     * 构造服务器：内部创建应用管理器并持有配置，不创建任何线程与通道资源；
     * 配置本身不做校验，启用http2未配ssl等校验在 {@link #start(int)} 启动时（绑定端口前）完成；
     * 配置为null立即抛出 {@link NullPointerException}（需要默认配置请使用无参构造器）
     *
     * @param serverConfig 服务器配置
     */
    public TurboWebHttpServer(ServerConfig serverConfig) {
        this.applicationManager = new ApplicationManager();
        this.serverConfig = Objects.requireNonNull(serverConfig, "The ServerConfig must not be null");
    }

    /**
     * 注册应用，装配每条连接时最先执行；返回本服务器实例便于链式注册。
     * <p>应用列表须在服务器启动前确定（装配会被多个worker线程并发执行），启动后再注册抛出
     * {@link IllegalStateException}；应用为null时由内部应用管理器抛出 {@link NullPointerException}。
     *
     * @param application 应用
     * @return 本服务器实例
     */
    public TurboWebHttpServer registerApplicationFirst(Application application) {
        checkNotStarted();
        applicationManager.addFirst(application);
        return this;
    }

    /**
     * 注册应用，装配每条连接时最后执行；返回本服务器实例便于链式注册。
     * <p>应用列表须在服务器启动前确定（装配会被多个worker线程并发执行），启动后再注册抛出
     * {@link IllegalStateException}；应用为null时由内部应用管理器抛出 {@link NullPointerException}。
     *
     * @param application 应用
     * @return 本服务器实例
     */
    public TurboWebHttpServer registerApplicationLast(Application application) {
        checkNotStarted();
        applicationManager.addLast(application);
        return this;
    }

    /**
     * 校验应用注册发生在服务器启动之前：启动后应用列表由多个worker线程并发读取，
     * 再修改会产生竞态，故以bootstrap是否已装配作为启动标志拦截
     */
    private void checkNotStarted() {
        if (this.serverBootstrap != null) {
            throw new IllegalStateException("Applications must be registered before the server starts");
        }
    }

    /**
     * 启动服务器，监听本机所有网卡地址。线程组、服务端通道与bootstrap等资源在首次启动时创建。
     *
     * @param port 监听端口
     * @return 启动结果的异步 {@link ChannelFuture}，需要阻塞等待时调用方自行sync
     */
    public ChannelFuture start(int port) {
        return start("0.0.0.0", port);
    }

    /**
     * 启动服务器，异步绑定指定地址；绑定成功后记录日志，失败时释放线程资源。
     *
     * @param host 监听主机地址
     * @param port 监听端口
     * @return 启动结果的异步 {@link ChannelFuture}
     */
    public ChannelFuture start(String host, int port) {
        startup();
        long begin = System.currentTimeMillis();
        ChannelFuture channelFuture = serverBootstrap.bind(host, port);
        channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                long time = System.currentTimeMillis() - begin;
                log.info("TurboWebHttpServer start on: http://{}:{}, time: {}ms", host, port, time);
            } else {
                shutdown();
                log.error("TurboWebHttpServer start failed: {}", future.cause().getMessage(), future.cause());
            }
        });
        return channelFuture;
    }

    /**
     * 启动期资源创建：先做配置校验（启用http2必须配置ssl），再创建boss/worker线程组、服务端通道、
     * 连接限流器与管道装配器，全部只在首次启动时创建一次，线程数与通道类型由服务器配置决定
     */
    private synchronized void startup() {
        if (this.serverBootstrap != null) {
            // 资源已创建（重复启动调用直接复用同一bootstrap）
            return;
        }
        // h2依赖ALPN协商，ssl为启用http2的前置要求；在任何资源创建前 fail-fast
        if (serverConfig.isEnableHttp2() && serverConfig.getSslContext() == null) {
            throw new IllegalArgumentException("SSL is required for HTTP/2");
        }
        // 线程组：boss固定1个线程处理accept，worker线程数取配置的ioThreadNum
        // （netty 4.2起NioEventLoopGroup已废弃，按官方迁移指引使用NioIoHandler工厂的MultiThreadIoEventLoopGroup）
        this.bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(serverConfig.getIoThreadNum(), NioIoHandler.newFactory());
        // 通道选择：未启用ssl使用零拷贝通道（零拷贝线程数取配置），启用ssl使用普通通道（零拷贝与ssl互斥）
        SslContext sslContext = serverConfig.getSslContext();
        if (sslContext == null) {
            // 创建专门用于零拷贝的线程池
            ThreadPoolExecutor zeroCopyExecutor = new ThreadPoolExecutor(
                    serverConfig.getZeroCopyThreadNum(),
                    serverConfig.getZeroCopyThreadNum(),
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new ZeroCopyThreadFactory()
            );
            // 允许核心线程过期
            zeroCopyExecutor.allowCoreThreadTimeOut(true);
            this.serverChannel = new TurboWebNioServerSocketChannel(zeroCopyExecutor);
        } else {
            this.serverChannel = new NioServerSocketChannel();
        }
        // 连接限流器：服务器级单例（@Sharable），此处一次性创建，跨全部连接复用
        ConnectLimiterChannelHandler connectLimiter = new ConnectLimiterChannelHandler(
                serverConfig.getMaxConnect(), serverConfig.getIoThreadNum(), serverConfig.getCpuNum());
        // 装配bootstrap
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.channelFactory(() -> this.serverChannel);
        // 每条子连接由装配器按 限流 → ssl → 编解码（或http2协议协商）→ 应用 的顺序装配，
        // 应用部分委托应用管理器追加
        bootstrap.childHandler(new ServerChannelInitializer(applicationManager, connectLimiter, serverConfig));
        this.serverBootstrap = bootstrap;
    }

    /**
     * 优雅关闭服务器，先关闭boss线程组，再关闭worker线程组；从未启动过时为空操作（无线程资源）。
     * <p>零拷贝线程池由 {@link TurboWebNioServerSocketChannel} 的通道关闭逻辑负责释放。</p>
     */
    public void shutdown() {
        EventLoopGroup boss = this.bossGroup;
        EventLoopGroup worker = this.workerGroup;
        if (boss == null || worker == null) {
            return;
        }
        boss.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                worker.shutdownGracefully().addListener(f -> {
                    if (f.isSuccess()) {
                        log.info("server is stop success");
                    } else {
                        log.error("worker thread shutdown err");
                    }
                });
            } else {
                log.error("boss thread shutdown err");
            }
        });
    }

    /**
     * 关闭服务器：先关闭服务端监听通道（零拷贝通道的关闭逻辑会同时释放零拷贝线程池），
     * 再调用 {@link #shutdown()} 优雅关闭boss与worker线程组，最后调用
     * {@link ApplicationManager#close()} 按注册顺序关闭全部应用，由各应用释放自己装配的资源；
     * 从未启动过时只跳过通道关闭（线程组与应用资源仍需释放）。
     * <p>
     * 应用关闭排在传输层之后，保证不再有新的请求进入应用处理器链后再回收其线程池。
     * <p>实现 {@link AutoCloseable}，支持try-with-resources与容器生命周期托管回调。</p>
     */
    @Override
    public void close() {
        ServerChannel channel = this.serverChannel;
        if (channel != null) {
            channel.close();
        }
        shutdown();
        applicationManager.close();
    }
}
