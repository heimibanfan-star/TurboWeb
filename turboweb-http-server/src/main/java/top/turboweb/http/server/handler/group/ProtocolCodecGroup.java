package top.turboweb.http.server.handler.group;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

/**
 * 协议编解码分组（不可变），构造器只接收本分组的可配置参数（ssl上下文与聚合器最大内容长度），
 * 托管ssl及其依赖的http编解码。
 * <p>
 * http协议编解码是http服务器的必备环节：本分组恒定生成——配置过直接使用创建的分组，
 * 未配置时使用默认分组（{@link #ProtocolCodecGroup()}，不启用ssl、聚合器取默认最大内容长度）。
 * 分组内处理器均每连接新建：
 * <ul>
 *     <li>HttpServerCodec与HttpObjectAggregator：恒定装配，均每连接新建，聚合器最大
 *     内容长度由构造参数指定（必须大于0）；</li>
 *     <li>SslHandler：组内唯一可选项，{@link SslContext} 可共享，装配时据此每连接新建
 *     独立实例，sslContext为null（未启用SSL）时不装配。</li>
 * </ul>
 * 装配位置在限流分组之后、http请求分组之前。各处理器装配前均按类型去重。
 */
public final class ProtocolCodecGroup implements NettyHandlerGroup {

    /**
     * 默认http聚合器最大内容长度（10MB），未配置本分组时默认分组使用该值
     */
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 10;

    /**
     * ssl上下文，装配时据此每连接新建SslHandler，未启用SSL时为null
     */
    private final SslContext sslContext;

    /**
     * http聚合器最大内容长度，恒大于0（构造时校验）
     */
    private final int maxContentLength;

    /**
     * 默认分组：不启用ssl，聚合器最大内容长度取 {@link #DEFAULT_MAX_CONTENT_LENGTH}
     */
    public ProtocolCodecGroup() {
        this(null, DEFAULT_MAX_CONTENT_LENGTH);
    }

    public ProtocolCodecGroup(SslContext sslContext, int maxContentLength) {
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength must be greater than 0");
        }
        this.sslContext = sslContext;
        this.maxContentLength = maxContentLength;
    }

    /**
     * 获取ssl上下文，可能为null（未启用SSL）
     */
    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * 获取http聚合器最大内容长度（恒大于0）
     */
    public int getMaxContentLength() {
        return maxContentLength;
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        // ssl处理器：ssl上下文可共享，SslHandler每连接新建独立实例（未启用SSL不装配）
        if (sslContext != null && pipeline.get(SslHandler.class) == null) {
            pipeline.addLast(new SslHandler(sslContext.newEngine(pipeline.channel().alloc())));
        }
        // http编解码与聚合器：必备环节恒装配，均每连接新建，聚合器最大内容长度按建造参数装配
        if (pipeline.get(HttpServerCodec.class) == null && pipeline.get(HttpObjectAggregator.class) == null) {
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        }
    }
}
