package top.turboweb.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.server.channel.TurboWebNioServerSocketChannel;
import top.turboweb.http.server.config.HttpServerConfig;
import top.turboweb.http.server.handler.ConnectLimiter;
import top.turboweb.http.server.handler.RequestSerializerHandler;
import top.turboweb.http.server.handler.group.ConnectLimiterGroup;
import top.turboweb.http.server.handler.group.HttpRequestGroup;
import top.turboweb.http.server.handler.group.ProtocolCodecGroup;
import top.turboweb.http.server.handler.manager.NettyHandlerManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TurboWeb http服务器（不可变）。
 * <p>
 * 参考旧版 top.turboweb.core.server 的启动链路（TurboWebServer接口 → CoreTurboWebServer抽象类
 * → BootStrapTurboWebServer）重构为单个final类：去掉接口与抽象基类分层、Initializer与工厂体系、
 * Listener机制、front/back处理器扩展点，不再支持网关与HTTP/2，只保留真正干活的服务器逻辑：
 * <ul>
 *     <li>线程模型：1个boss线程 + ioThreadNum个工作线程（默认取cpu核数） +
 *     zeroCopyThreadNum个零拷贝线程（默认cpu核数*2，守护线程且允许核心线程超时）；</li>
 *     <li>通道选择：未启用SSL时使用零拷贝 {@link TurboWebNioServerSocketChannel}，
 *     启用SSL时使用普通 {@link NioServerSocketChannel}（二者互斥，与旧版逻辑一致）；</li>
 *     <li>pipeline装配：服务器不单独装配任何handler，initChannel时直接调用
 *     {@link NettyHandlerManager#configure(ChannelPipeline)} 由管理器按处理器分组统一装配全部处理器：
 *     限流分组（{@link ConnectLimiter} 单例，addFirst置于管道最前）、协议编解码分组（HttpServerCodec与
 *     HttpObjectAggregator恒每连接新建装配，启用SSL时SslHandler每连接新建）、http请求分组（每连接新建的
 *     {@link RequestSerializerHandler} 与单例http协议分发器）、websocket分组（单例websocket初始化handler）；
 *     服务器构建时只补全管理器建造者上未配置的分组（限流分组按config生成、协议编解码分组按sslContext与config生成、
 *     config开启串行化且未配置http请求分组时补仅装配串行化处理器的分组），使用者已自行配置的分组不会被覆盖，
     *     分组顺序与组内单例复用/每连接新建的策略全部由 {@link NettyHandlerManager} 内部管理。</li>
 * </ul>
 * 服务器不做优雅停机回调、生命周期监听等扩展，启动返回Netty的 {@link ChannelFuture}，
 * 调用方可自行 sync 或注册监听。
 *
 * <pre>{@code
 * HttpServerConfig config = HttpServerConfig.builder().build();
 * TurboWebHttpServer server = TurboWebHttpServer.builder()
 *         .config(config)
 *         // 处理器装配在管理器的建造者上按"一个方法产出一个分组"链式配置，未配置的分组由服务器按config与建造者设置自动补全
 *         .handlerManager(NettyHandlerManager.builder()
 *                 .enableHttpRequest(true, httpScheduler) // 第一个参数true表示每连接装配串行化处理器
 *                 .enableWebSocket("/ws/.*", myWebSocketHandler))
 *         .build();
 * // 异步启动，监听8080端口；需要阻塞等待时自行 sync
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
        public Thread newThread(Runnable r) {
            count.compareAndSet(Long.MAX_VALUE, 0);
            Thread thread = new Thread(r, "zero-copy-thread-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 服务器配置
     */
    private final HttpServerConfig config;

    /**
     * 业务处理器管理器，按处理器分组统一托管装配顺序及组内单例/每连接新建策略；
     * 构造时由建造者传入的管理器建造者补全未配置的分组后构建
     */
    private final NettyHandlerManager handlerManager;

    /**
     * io工作线程数
     */
    private final int ioThreadNum;

    /**
     * ssl上下文，为null表示未启用ssl
     */
    private final SslContext sslContext;

    /**
     * 服务端通道，构造时按是否启用ssl确定零拷贝通道或普通通道
     */
    private final ServerChannel serverChannel;

    /**
     * boss线程组（处理accept事件）
     */
    private final NioEventLoopGroup bossGroup;

    /**
     * worker线程组（处理io事件）
     */
    private final NioEventLoopGroup workerGroup;

    /**
     * netty引导对象
     */
    private final ServerBootstrap serverBootstrap;

    private TurboWebHttpServer(Builder builder) {
        this.config = builder.config;
        this.ioThreadNum = builder.ioThreadNum;
        this.sslContext = builder.sslContext;
        // 创建线程组
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup(this.ioThreadNum);
        // 服务器只补全建造者上未配置的分组（已配置的分组不覆盖）：限流分组按config生成，
        // 协议编解码分组按sslContext与config生成，config开启串行化且未配置http请求分组时补仅装配串行化处理器的分组
        NettyHandlerManager.Builder managerBuilder = builder.handlerManagerBuilder;
        if (!managerBuilder.isConfigured(ConnectLimiterGroup.class)) {
            managerBuilder.enableConnectLimiter(config.getMaxConnections(), this.ioThreadNum, config.getCpuNum());
        }
        if (!managerBuilder.isConfigured(ProtocolCodecGroup.class)) {
            managerBuilder.protocolCodec(this.sslContext, config.getMaxContentLength());
        }
        if (!managerBuilder.isConfigured(HttpRequestGroup.class) && config.isSerializePerConnection()) {
            managerBuilder.enableHttpRequest(true, null);
        }
        this.handlerManager = managerBuilder.build();
        // 通道选择：未启用ssl使用零拷贝通道，启用ssl使用普通通道（零拷贝与ssl互斥）
        if (this.sslContext == null) {
            // 创建专门用于零拷贝的线程池
            ThreadPoolExecutor zeroCopyExecutor = new ThreadPoolExecutor(
                    builder.zeroCopyThreadNum,
                    builder.zeroCopyThreadNum,
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
        // 装配bootstrap
        this.serverBootstrap = new ServerBootstrap();
        this.serverBootstrap.group(bossGroup, workerGroup)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        this.serverBootstrap.channelFactory(() -> this.serverChannel);
        this.serverBootstrap.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                // 限流、ssl、http编解码聚合、串行化与业务处理器全部由管理器统一装配
                handlerManager.configure(ch.pipeline());
            }
        });
    }

    /**
     * 创建服务器构建器，io线程与零拷贝线程默认取配置推导值
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取服务器配置
     */
    public HttpServerConfig getConfig() {
        return config;
    }

    /**
     * 获取业务处理器管理器
     */
    public NettyHandlerManager getHandlerManager() {
        return handlerManager;
    }

    /**
     * 获取io工作线程数
     */
    public int getIoThreadNum() {
        return ioThreadNum;
    }

    /**
     * 启动服务器，监听本机所有网卡地址。
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
     * 优雅关闭服务器，先关闭boss线程组，再关闭worker线程组。
     * <p>零拷贝线程池由 {@link TurboWebNioServerSocketChannel} 的通道关闭逻辑负责释放。</p>
     */
    public void shutdown() {
        bossGroup.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                workerGroup.shutdownGracefully().addListener(f -> {
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
     * 再调用 {@link #shutdown()} 优雅关闭boss与worker线程组。
     * <p>实现 {@link AutoCloseable}，支持try-with-resources与容器生命周期托管回调。</p>
     */
    @Override
    public void close() {
        serverChannel.close();
        shutdown();
    }

    /**
     * {@link TurboWebHttpServer} 的建造者，链式方法校验非法参数立即抛出 {@link IllegalArgumentException}。
     * ioThreadNum 默认取 config 的 cpuNum，zeroCopyThreadNum 默认取 cpuNum 的 2 倍；
     * 未调用 sslContext 时服务器不启用ssl并使用零拷贝通道。
     */
    public static final class Builder {

        private HttpServerConfig config;

        /**
         * 业务处理器管理器建造者，build时由服务器补全其中未配置的分组
         */
        private NettyHandlerManager.Builder handlerManagerBuilder;

        /**
         * io线程数，0表示未设置（build时取cpu核数）
         */
        private int ioThreadNum = 0;

        /**
         * 零拷贝线程数，0表示未设置（build时取cpu核数*2）
         */
        private int zeroCopyThreadNum = 0;

        private SslContext sslContext;

        private Builder() {
        }

        /**
         * 设置服务器配置（最大内容长度、最大连接数、cpu核数、是否每连接串行化等）
         *
         * @param config 服务器配置，不能为null
         */
        public Builder config(HttpServerConfig config) {
            if (config == null) {
                throw new IllegalArgumentException("config can not be null");
            }
            this.config = config;
            return this;
        }

        /**
         * 设置业务处理器管理器建造者（enableConnectLimiter/protocolCodec/enableHttpRequest/enableWebSocket
         * 一个方法产出一个分组，在这里配置，分组对象由建造者内部创建、不对外暴露；未配置的分组由服务器构建时按config与sslContext自动补全
         * （限流分组、协议编解码分组，以及config开启串行化时的http请求分组），已自行配置的分组不会被覆盖——
         * 自行配置协议编解码分组时ssl以分组参数为准、config的maxContentLength不再生效，自行配置http请求分组时
         * 串行化以分组参数为准、config的serializePerConnection不再生效；协议编解码为必备环节恒定装配）
         *
         * @param handlerManager 处理器管理器建造者，不能为null
         */
        public Builder handlerManager(NettyHandlerManager.Builder handlerManager) {
            if (handlerManager == null) {
                throw new IllegalArgumentException("handlerManager can not be null");
            }
            this.handlerManagerBuilder = handlerManager;
            return this;
        }

        /**
         * 设置io工作线程数，不设置时默认取config的cpuNum
         *
         * @param ioThreadNum 线程数，必须大于0
         */
        public Builder ioThreadNum(int ioThreadNum) {
            if (ioThreadNum <= 0) {
                throw new IllegalArgumentException("ioThreadNum must be greater than 0");
            }
            this.ioThreadNum = ioThreadNum;
            return this;
        }

        /**
         * 设置零拷贝线程数，不设置时默认取config的cpuNum*2（启用ssl后不生效）
         *
         * @param zeroCopyThreadNum 线程数，必须大于0
         */
        public Builder zeroCopyThreadNum(int zeroCopyThreadNum) {
            if (zeroCopyThreadNum <= 0) {
                throw new IllegalArgumentException("zeroCopyThreadNum must be greater than 0");
            }
            this.zeroCopyThreadNum = zeroCopyThreadNum;
            return this;
        }

        /**
         * 启用ssl，设置后使用普通Nio通道（零拷贝与ssl互斥）
         *
         * @param sslContext ssl上下文，不能为null
         */
        public Builder sslContext(SslContext sslContext) {
            if (sslContext == null) {
                throw new IllegalArgumentException("sslContext can not be null");
            }
            this.sslContext = sslContext;
            return this;
        }

        /**
         * 构建服务器实例，线程数缺省时按config推导
         */
        public TurboWebHttpServer build() {
            if (config == null) {
                throw new IllegalArgumentException("config不能为空");
            }
            if (handlerManagerBuilder == null) {
                throw new IllegalArgumentException("handlerManager不能为空");
            }
            // io线程默认取cpu核数
            if (ioThreadNum <= 0) {
                ioThreadNum = config.getCpuNum();
            }
            // 零拷贝线程默认取cpu核数的2倍
            if (zeroCopyThreadNum <= 0) {
                zeroCopyThreadNum = config.getCpuNum() * 2;
            }
            return new TurboWebHttpServer(this);
        }
    }
}
