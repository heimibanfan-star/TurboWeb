package top.turboweb.http.server.handler.group;

import io.netty.channel.ChannelPipeline;
import top.turboweb.http.server.handler.ConnectLimiter;

/**
 * 连接限流分组（不可变），构造器只接收本分组的可配置参数，不对外暴露处理器实例的传入。
 * <p>
 * 构造参数为最大连接数、io工作线程数与cpu核数，分组内部据此创建服务器级单例
 * {@link ConnectLimiter}（标注 {@link io.netty.channel.ChannelHandler.Sharable}，跨channel复用），
 * 装配时addFirst置于管道最前，先于协议编解码分组监听连接生命周期事件；
 * 装配位置全部第一。管道中已存在同类型限流器时跳过。
 */
public final class ConnectLimiterGroup implements NettyHandlerGroup {

    /**
     * 连接限流器（服务器级单例，分组构造时按可配置参数内部创建）
     */
    private final ConnectLimiter connectLimiter;

    public ConnectLimiterGroup(int maxConnect, int ioThreadNum, int cpuNum) {
        if (maxConnect <= 0) {
            throw new IllegalArgumentException("maxConnect must be greater than 0");
        }
        // 限流器为服务器级单例：分组构造时创建一次，跨channel复用（io线程数与cpu核数校验由限流器内部完成）
        this.connectLimiter = new ConnectLimiter(maxConnect, ioThreadNum, cpuNum);
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        // 连接限流器：服务器级单例实例，置于管道最前（先于ssl与编解码）
        if (pipeline.get(ConnectLimiter.class) == null) {
            pipeline.addFirst(connectLimiter);
        }
    }
}
