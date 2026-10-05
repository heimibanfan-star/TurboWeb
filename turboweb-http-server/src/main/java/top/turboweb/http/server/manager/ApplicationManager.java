package top.turboweb.http.server.manager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.server.application.Application;
import top.turboweb.http.server.config.ServerConfig;
import top.turboweb.http.server.handler.ConnectLimiter;

import java.util.LinkedList;
import java.util.Objects;

/**
 * 应用管理器：基础处理器与应用在连接管道上的统一装配入口。
 * <p>
 * 管理器内置一份默认 {@link ServerConfig}（{@code ServerConfig.builder().build()}），
 * 需要修改时通过 {@link #config(ServerConfig)} 整体替换，配置仅在
 * {@link #configure(ChannelPipeline)} 内部被消费；每次设置配置都会据其限流参数重建
 * 服务器级单例 {@link ConnectLimiter}，并直接管理ssl、http编解码与http2等基础处理器的
 * 装配（不走 {@link top.turboweb.http.server.handler.group.NettyHandlerGroup} 分组体系）。
 * <p>
 * {@link #configure(ChannelPipeline)} 在每条新连接建立时被调用一次，装配顺序固定为：
 * <ol>
 *     <li>限流：连接限流器为服务器级单例（{@code @Sharable}）跨连接复用，addFirst置于管道最前；</li>
 *     <li>ssl：ssl上下文可共享，{@link SslHandler} 每连接新建，未配置ssl上下文时不装配；</li>
 *     <li>编解码：未启用http2时每连接新建 {@link HttpServerCodec} 与 {@link HttpObjectAggregator}
 *     （聚合器最大内容长度取服务器配置），随后按注册顺序装配应用；启用http2时ssl为前置要求
 *     （{@code config} 配置期校验），改装 {@link ApplicationProtocolNegotiationHandler}：ALPN协商为h2时装配
 *     {@link Http2FrameCodec} 与 {@link Http2MultiplexHandler}，每条子流管道装好流编解码与
 *     聚合器后再装配应用；协商降级为http/1.1时回退装配http编解码与聚合器后装配应用；</li>
 *     <li>应用：按 {@link #addFirst(Application)} / {@link #addLast(Application)} 的注册顺序
 *     依次调用各应用的configure追加到管道尾部。</li>
 * </ol>
 * 启用http2时应用不会直接追加在连接管道上（协商回调内才装配协议编解码，应用须排在其后），
 * 统一经由协商回调装配到协议层就绪的管道（连接管道或子流管道）。
 * <p>
 * 配置的替换与应用的注册须在服务器启动前完成：{@code configure} 会被多个worker线程并发调用，
 * 启动后再修改配置或应用列表存在并发风险。
 *
 * <pre>{@code
 * ApplicationManager manager = new ApplicationManager()
 *         .config(ServerConfig.builder().maxConnect(20000).build())
 *         .addLast(httpRequestApplication)
 *         .addLast(webSocketApplication);
 * // 服务器childHandler中：manager.configure(ch.pipeline());
 * }</pre>
 */
public class ApplicationManager {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ApplicationManager.class);

    /**
     * 服务器配置（不可变对象整体替换），默认取 {@code ServerConfig.builder().build()}，
     * 仅在 configure 内部消费；volatile 保证启动前替换后对各worker线程立即可见
     */
    private volatile ServerConfig serverConfig;

    /**
     * 连接限流器（服务器级单例，随配置创建/替换，跨channel复用，装配时addFirst置于管道最前）
     */
    private volatile ConnectLimiter connectLimiter;

    /**
     * 已注册的应用列表，装配顺序即列表顺序（addFirst插队头、addLast排队尾）
     */
    private final LinkedList<Application> applications = new LinkedList<>();

    public ApplicationManager() {
        // 未显式设置时使用默认配置
        this.config(ServerConfig.builder().build());
    }

    /**
     * 整体替换服务器配置：null立即抛出 {@link NullPointerException}；
     * h2依赖ALPN协商，ssl为启用http2的前置要求，启用http2而未配置ssl上下文时抛出
     * {@link IllegalArgumentException}；每次替换都会按新配置的限流参数重建
     * 服务器级单例 {@link ConnectLimiter}。配置替换须在服务器启动前完成
     */
    public ApplicationManager config(ServerConfig serverConfig) {
        Objects.requireNonNull(serverConfig, "ServerConfig不能为null");
        if (serverConfig.isEnableHttp2() && serverConfig.getSslContext() == null) {
            throw new IllegalArgumentException("SSL is required for HTTP/2");
        }
        // 限流器为服务器级单例：随配置重建一次，跨channel复用（参数校验由限流器内部完成）
        this.connectLimiter = new ConnectLimiter(
                serverConfig.getMaxConnect(), serverConfig.getIoThreadNum(), serverConfig.getCpuNum());
        this.serverConfig = serverConfig;
        log.info("应用管理器配置完成：最大连接数 {}, io线程数 {}, cpu核数 {}, ssl: {}, http2: {}, 聚合器最大内容长度 {}",
                serverConfig.getMaxConnect(), serverConfig.getIoThreadNum(), serverConfig.getCpuNum(),
                serverConfig.getSslContext() != null, serverConfig.isEnableHttp2(),
                serverConfig.getMaxContentLength());
        return this;
    }

    /**
     * 将应用插到应用列表头部（装配时最先执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ApplicationManager addFirst(Application application) {
        Objects.requireNonNull(application, "应用不能为null");
        this.applications.addFirst(application);
        return this;
    }

    /**
     * 将应用追加到应用列表尾部（装配时最后执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ApplicationManager addLast(Application application) {
        Objects.requireNonNull(application, "应用不能为null");
        this.applications.addLast(application);
        return this;
    }

    /**
     * 每条新连接建立时被调用一次，按 限流 → ssl → 编解码（或协议协商）→ 应用 的固定顺序装配管道，
     * 各处理器装配前均按类型去重
     *
     * @param pipeline 新连接的处理器链，不能为null
     */
    public void configure(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline can not be null");
        }
        // 一次性捕获当前配置快照，本次连接的全部装配（含协商回调）都基于同一份配置
        ServerConfig config = this.serverConfig;
        // 限流：服务器级单例，置于管道最前（先于ssl与编解码监听连接生命周期）
        if (pipeline.get(ConnectLimiter.class) == null) {
            pipeline.addFirst(connectLimiter);
        }
        // ssl：上下文可共享，SslHandler每连接新建独立实例（未配置ssl上下文时不装配）
        SslContext sslContext = config.getSslContext();
        if (sslContext != null && pipeline.get(SslHandler.class) == null) {
            pipeline.addLast(new SslHandler(sslContext.newEngine(pipeline.channel().alloc())));
        }
        if (config.isEnableHttp2()) {
            // http2：装协议协商处理器，编解码与应用在ALPN协商结果确定后装配（应用不能直接追加在协商处理器之后，
            // 否则降级分支中协商回调内新建的编解码器会排在应用之后）
            pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                        configureHttp2(ctx.pipeline(), config);
                    } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                        // 协商降级为http/1.1：回退装配http编解码与聚合器后装配应用
                        configureHttp11(ctx.pipeline(), config);
                    } else {
                        throw new IllegalStateException("unknown protocol: " + protocol);
                    }
                }
            });
        } else {
            // http/1.1：编解码每连接新建，聚合器最大内容长度取服务器配置，随后按注册顺序装配应用
            configureHttp11(pipeline, config);
        }
        log.debug("连接[{}]装配基础处理器与应用完成（应用 {} 个，http2: {}）",
                pipeline.channel().id().asShortText(), applications.size(), config.isEnableHttp2());
    }

    /**
     * 装配http2处理器：帧编解码器（携带服务器配置中的http2设置）与多路复用处理器，
     * 每条子流管道装好流编解码与聚合器（聚合后子流收到的即为FullHttpRequest）后装配应用
     */
    private void configureHttp2(ChannelPipeline pipeline, ServerConfig serverConfig) {
        Http2FrameCodec frameCodec = Http2FrameCodecBuilder
                .forServer()
                .initialSettings(serverConfig.getHttp2Settings())
                .build();
        pipeline.addLast(frameCodec);
        pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline streamPipeline = ch.pipeline();
                // 子流双向编解码（服务端方向）与聚合器：聚合后子流管道收到的即为FullHttpRequest
                streamPipeline.addLast(new Http2StreamFrameToHttpObjectCodec(true));
                streamPipeline.addLast(new HttpObjectAggregator(serverConfig.getMaxContentLength()));
                // 协议层就绪，按注册顺序装配应用
                configureApplications(streamPipeline);
            }
        }));
    }

    /**
     * 装配http/1.1编解码（每连接新建）并随后按注册顺序装配应用，
     * 同时用于未启用http2的连接管道与http2协商降级的回退装配，装配前按类型去重
     */
    private void configureHttp11(ChannelPipeline pipeline, ServerConfig serverConfig) {
        if (pipeline.get(HttpServerCodec.class) == null && pipeline.get(HttpObjectAggregator.class) == null) {
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(serverConfig.getMaxContentLength()));
        }
        configureApplications(pipeline);
    }

    /**
     * 按注册顺序依次调用各应用configure，将应用处理器追加到管道尾部
     */
    private void configureApplications(ChannelPipeline pipeline) {
        for (Application application : applications) {
            application.configure(pipeline);
        }
    }
}
