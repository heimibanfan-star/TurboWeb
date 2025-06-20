package top.turboweb.core.initializer;

import top.turboweb.core.config.HttpServerConfig;

/**
 * 公共资源初始化器
 */
public interface CommonSourceInitializer {

    /**
     * 初始化公共资源
     *
     * @param config 服务器参数配置
     */
    void init(HttpServerConfig config);
}
