package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import top.turboweb.commons.utils.resource.ScopedResource;

/**
 * netty应用，用于管理上游的处理器。
 * <p>
 * 一个应用实例封装一段完整的应用级处理器装配逻辑，在每条新连接建立时被调用一次，
 * 通过 {@link #configure(ChannelPipeline)} 将应用相关的处理器追加到pipeline尾部。
 * 同一实例会被多个worker线程并发复用，实现类必须保证自身线程安全：
 * 连接级有状态处理器（如请求串行化处理器）需每次装配新建实例，无状态处理器应作为单例直接添加。
 * <p>
 * 应用同时是其装配期资源的持有者与释放者：实现类持有一个 {@link ScopedResource} 资源作用域，
 * 装配期创建的生命周期资源（线程池、中间件链、缓存等）逐个登记进来，关闭由本接口的默认
 * {@link #close()} 统一触发——按登记顺序释放整个作用域，作用域只释放一次（重复关闭幂等），
 * 单个资源释放失败只记录日志不中断其余释放，因此实现类无须自己编写关闭逻辑。
 * {@code TurboWebHttpServer} 关闭时会按注册顺序统一触发全部已注册应用的 {@code close()}，
 * 调用方也可对应用实例直接使用try-with-resources或显式关闭。
 */
public interface Application extends AutoCloseable {

    /**
     * 配置netty的处理器链，将应用相关的处理器按顺序追加到pipeline尾部
     *
     * @param pipeline 新连接的处理器链
     */
    void configure(ChannelPipeline pipeline);

    /**
     * 获取应用持有的资源作用域：装配期的可关闭资源登记于此，关闭时统一释放
     *
     * @return 资源作用域，不能为null
     */
    ScopedResource getScopedResource();

    /**
     * 关闭应用：释放 {@link #getScopedResource()} 中登记的全部资源。
     * <p>
     * 重写 {@link AutoCloseable#close()} 并收窄为不抛受检异常；释放过程中的失败由
     * {@link ScopedResource} 内部记录日志，重复调用为空操作。
     */
    @Override
    default void close() {
        getScopedResource().close();
    }
}
