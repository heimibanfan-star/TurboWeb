package top.turboweb.core.initializer;

import io.netty.channel.EventLoopGroup;
import top.turboweb.client.config.HttpClientConfig;

import java.util.function.Consumer;

/**
 * http客户端初始化器
 */
public interface HttpClientInitializer {

    /**
     * 配置http客户端
     *
     * @param consumer 进行参数配置的回调
     */
    void config(Consumer<HttpClientConfig> consumer);

    /**
     * 执行初始化操作
     * @param group 事件循环线程组
     */
    void init(EventLoopGroup group);

    /**
     * 替换配置
     * @param httpClientConfig 配置
     */
    void replaceConfig(HttpClientConfig httpClientConfig);
}
