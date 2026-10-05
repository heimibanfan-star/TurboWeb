package top.turboweb.http.server.handler.group;

import io.netty.channel.ChannelPipeline;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.server.handler.HttpProtocolDispatcher;
import top.turboweb.http.server.handler.RequestSerializerHandler;

/**
 * http请求分组（不可变），构造器只接收本分组的可配置参数，不对外暴露处理器实例的传入；
 * 托管请求顺序控制器与http协议分发器。
 * <p>
 * 构造参数为串行化开关与http调度器，分组内部据调度器创建单例分发器；分组内装配顺序与复用方式：
 * <ul>
 *     <li>{@link RequestSerializerHandler}：<b>每连接新建</b>，持有连接级状态（缓冲队列与
 *     处理中标记）不可复用，装配在分组第一位；serializePerConnection为false时不装配；</li>
 *     <li>{@link HttpProtocolDispatcher}：分组构造时依据 {@link HttpScheduler} 创建的<b>单例</b>，
 *     跨channel复用，装配在串行化之后，处理普通http请求，并把携带Upgrade头的http升级协议请求
 *     向后转发；调度器为null（未启用http）时不装配。</li>
 * </ul>
 * 装配位置在协议编解码分组之后、websocket分组之前。各处理器装配前均按类型去重。
 */
public final class HttpRequestGroup implements NettyHandlerGroup {

    /**
     * 是否为每条连接装配请求串行化处理器（处理器实例本身每连接新建）
     */
    private final boolean serializePerConnection;

    /**
     * http调度器，分组可配置参数，未启用http分发器时为null
     */
    private final HttpScheduler httpScheduler;

    /**
     * http协议分发器（单例，分组构造时据调度器内部创建），装配顺序位于串行化之后，未启用http时为null
     */
    private final HttpProtocolDispatcher httpProtocolDispatcher;

    public HttpRequestGroup(boolean serializePerConnection, HttpScheduler httpScheduler) {
        if (!serializePerConnection && httpScheduler == null) {
            throw new IllegalArgumentException("serializePerConnection and httpScheduler can not both be disabled");
        }
        this.serializePerConnection = serializePerConnection;
        this.httpScheduler = httpScheduler;
        // http分发器为单例：分组构造时依据调度器创建一次，跨channel复用
        this.httpProtocolDispatcher = httpScheduler == null ? null
                : HttpProtocolDispatcher.builder().httpScheduler(httpScheduler).build();
    }

    /**
     * 是否为每条连接装配请求串行化处理器
     */
    public boolean isSerializePerConnection() {
        return serializePerConnection;
    }

    /**
     * 获取http调度器，可能为null（未启用http分发器）
     */
    public HttpScheduler getHttpScheduler() {
        return httpScheduler;
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        // 请求串行化处理器：持有连接级状态，每连接新建独立实例
        if (serializePerConnection && pipeline.get(RequestSerializerHandler.class) == null) {
            pipeline.addLast(new RequestSerializerHandler());
        }
        // http协议分发器：单例复用，装配在串行化之后
        if (httpProtocolDispatcher != null && pipeline.get(HttpProtocolDispatcher.class) == null) {
            pipeline.addLast(httpProtocolDispatcher);
        }
    }
}
