package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;

/**
 * netty应用，用于管理上游的处理器。
 * <p>
 * 一个应用实例封装一段完整的应用级处理器装配逻辑，在每条新连接建立时被调用一次，
 * 通过 {@link #configure(ChannelPipeline)} 将应用相关的处理器追加到pipeline尾部。
 * 同一实例会被多个worker线程并发复用，实现类必须保证自身线程安全：
 * 连接级有状态处理器（如请求串行化处理器）需每次装配新建实例，无状态处理器应作为单例直接添加。
 */
public interface Application {

    /**
     * 配置netty的处理器链，将应用相关的处理器按顺序追加到pipeline尾部
     *
     * @param pipeline 新连接的处理器链
     */
    void configure(ChannelPipeline pipeline);
}
