package top.turboweb.server.runtime;

import io.netty.channel.ChannelPipeline;

/**
 * 服务器运行时契约。
 * <p>
 * 实现类是应用相关处理器在服务器上的装配入口：每条新连接建立时被调用一次，
 * 经 {@link #configure(ChannelPipeline)} 把处理器按顺序追加到处理器链尾部；
 * 实现类持有的运行期资源经 {@link AutoCloseable#close()} 统一释放。
 */
public interface ServerRuntime extends AutoCloseable {

    /**
     * 配置netty的处理器链，将应用相关的处理器按顺序追加到pipeline尾部
     *
     * @param pipeline 新连接的处理器链
     */
    void configure(ChannelPipeline pipeline);
}
