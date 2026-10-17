package top.turboweb.http.server.channel;

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
import top.turboweb.http.server.config.ServerConfig;
import top.turboweb.http.server.handler.ConnectLimiterChannelHandler;
import top.turboweb.http.server.manager.ApplicationManager;

import java.util.Objects;

/**
 * 服务器连接管道装配器：作为 {@code ServerBootstrap} 的 childHandler，在每条新连接建立时被调用一次，
 * 按 限流 → ssl → 编解码（或http2协议协商）→ 应用 的固定顺序装配连接管道。
 * <p>
 * 本类只负责服务器级传输处理器的装配，应用处理器统一委托 {@link ApplicationManager#configure(ChannelPipeline)}
 * 在协议层就绪后追加，因此应用始终排在协议编解码之后；服务器级参数（限流上限、ssl上下文、聚合器最大内容长度、
 * http2设置）全部取自构造时传入的 {@link ServerConfig}。
 * <p>
 * 装配顺序固定为：
 * <ol>
 *     <li>限流：连接限流器为服务器级单例（{@code @Sharable}）跨连接复用，由
 *     {@link top.turboweb.http.server.BootStrapWebServer} 启动时创建并传入，addFirst置于管道最前；</li>
 *     <li>ssl：ssl上下文可共享，{@link SslHandler} 每连接新建，未配置ssl上下文时不装配；</li>
 *     <li>编解码：未启用http2时每连接新建 {@link HttpServerCodec} 与 {@link HttpObjectAggregator}
 *     （聚合器最大内容长度取传入配置），随后委托应用管理器装配应用；启用http2时改装
 *     {@link ApplicationProtocolNegotiationHandler}：ALPN协商为h2时装配 {@link Http2FrameCodec} 与
 *     {@link Http2MultiplexHandler}，每条子流管道装好流编解码与聚合器后再装配应用；协商降级为http/1.1时
 *     回退装配http编解码与聚合器后装配应用；</li>
 *     <li>应用：委托 {@link ApplicationManager#configure(ChannelPipeline)} 按注册顺序追加到管道尾部。</li>
 * </ol>
 * 启用http2时应用不会直接追加在连接管道上（协商回调内才装配协议编解码，应用须排在其后），
 * 统一经由协商回调装配到协议层就绪的管道（连接管道或子流管道）。
 * <p>
 * 本实例由 {@code ServerBootstrap} 跨全部子连接复用，{@link #initChannel(Channel)} 会被多个worker线程
 * 并发调用；三个字段均为final且只读，装配过程无共享可变状态。
 *
 * <pre>{@code
 * ConnectLimiterChannelHandler limiter = new ConnectLimiterChannelHandler(maxConnect, ioThreadNum, cpuNum);
 * bootstrap.childHandler(new ServerChannelInitializer(applicationManager, limiter, serverConfig));
 * }</pre>
 */
public final class ServerChannelInitializer extends ChannelInitializer<Channel> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ServerChannelInitializer.class);

    /**
     * 应用管理器，协议层就绪后由它按注册顺序装配应用
     */
    private final ApplicationManager applicationManager;

    /**
     * 服务器级连接限流器单例，由服务器启动时创建（跨连接复用，装配时addFirst置于管道最前）
     */
    private final ConnectLimiterChannelHandler connectLimiter;

    /**
     * 服务器配置（不可变），限流之外的全部装配参数（ssl、聚合器长度、http2设置）均取自本配置
     */
    private final ServerConfig serverConfig;

    /**
     * 构造装配器，三个依赖均不能为null
     *
     * @param applicationManager 应用管理器
     * @param connectLimiter     服务器级连接限流器单例
     * @param serverConfig       服务器配置
     */
    public ServerChannelInitializer(ApplicationManager applicationManager, ConnectLimiterChannelHandler connectLimiter, ServerConfig serverConfig) {
        this.applicationManager = Objects.requireNonNull(applicationManager, "The application manager must not be null");
        this.connectLimiter = Objects.requireNonNull(connectLimiter, "The connection limiter must not be null");
        this.serverConfig = Objects.requireNonNull(serverConfig, "The ServerConfig must not be null");
    }

    /**
     * 装配新连接管道：限流置于最前，随后ssl，再按是否启用http2装协商处理器或直接装http/1.1编解码，
     * 各协议分支末尾统一委托应用管理器追加应用
     */
    @Override
    protected void initChannel(Channel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        // 限流：服务器级单例，置于管道最前（先于ssl与编解码监听连接生命周期）
        if (pipeline.get(ConnectLimiterChannelHandler.class) == null) {
            pipeline.addFirst(connectLimiter);
        }
        // ssl：上下文可共享，SslHandler每连接新建独立实例（未配置ssl上下文时不装配）
        SslContext sslContext = serverConfig.getSslContext();
        if (sslContext != null && pipeline.get(SslHandler.class) == null) {
            pipeline.addLast(new SslHandler(sslContext.newEngine(pipeline.channel().alloc())));
        }
        if (serverConfig.isEnableHttp2()) {
            // http2：装协议协商处理器，编解码与应用在ALPN协商结果确定后装配（应用不能直接追加在协商处理器之后，
            // 否则降级分支中协商回调内新建的编解码器会排在应用之后）
            pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                        configureHttp2(ctx.pipeline());
                    } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                        // 协商降级为http/1.1：回退装配http编解码与聚合器后装配应用
                        configureHttp11(ctx.pipeline());
                    } else {
                        throw new IllegalStateException("unknown protocol: " + protocol);
                    }
                }
            });
        } else {
            // http/1.1：编解码每连接新建，聚合器最大内容长度取服务器配置，随后委托应用管理器装配应用
            configureHttp11(pipeline);
        }
        log.debug("Base handlers and applications assembled for connection [{}] (http2: {})",
                pipeline.channel().id().asShortText(), serverConfig.isEnableHttp2());
    }

    /**
     * 装配http2处理器：帧编解码器（携带服务器配置中的http2设置）与多路复用处理器，
     * 每条子流管道装好流编解码与聚合器（聚合后子流收到的即为FullHttpRequest）后装配应用
     */
    private void configureHttp2(ChannelPipeline pipeline) {
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
                // 协议层就绪，委托应用管理器按注册顺序装配应用
                applicationManager.configure(streamPipeline);
            }
        }));
    }

    /**
     * 装配http/1.1编解码（每连接新建）并随后委托应用管理器装配应用，
     * 同时用于未启用http2的连接管道与http2协商降级的回退装配，装配前按类型去重
     */
    private void configureHttp11(ChannelPipeline pipeline) {
        if (pipeline.get(HttpServerCodec.class) == null && pipeline.get(HttpObjectAggregator.class) == null) {
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(serverConfig.getMaxContentLength()));
        }
        applicationManager.configure(pipeline);
    }
}