package top.turboweb.http.server.handler.group;

import io.netty.channel.ChannelPipeline;

/**
 * handler分组接口，每一类处理器归属一个独立的分组实现。
 * <p>
 * 分组内部负责本类处理器的装配（哪些单例复用、哪些每连接新建），并按类型去重，
 * 装配可在空管道上直接进行、允许重复调用；处理器的装配顺序与分组复用策略由
 * {@link top.turboweb.http.server.handler.manager.NettyHandlerManager} 按顺序统一管理，
 * 未启用对应处理器的分组不会参与装配。
 */
public interface NettyHandlerGroup {

    /**
     * 按本分组内部策略把已启用的处理器依次装配到管道，管道中已存在同类型处理器时跳过
     *
     * @param pipeline netty管道（可在空管道上直接调用）
     */
    void configure(ChannelPipeline pipeline);
}
