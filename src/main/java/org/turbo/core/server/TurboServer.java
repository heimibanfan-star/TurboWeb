package org.turbo.core.server;

import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.turbo.core.config.ServerParamConfig;
import org.turbo.core.http.middleware.Middleware;

/**
 * 服务器接口
 */
public interface TurboServer {

    /**
     * 启动服务器
     */
    public void start();

    /**
     * 启动服务器
     *
     * @param port 端口
     */
    public void start(int port);

    /**
     * 添加控制器
     *
     * @param controller 控制器
     */
    public void addController(Class<?> controller);

    /**
     * 添加控制器
     *
     * @param controllers 控制器
     */
    public void addController(Class<?>... controllers);

    /**
     * 设置配置
     *
     * @param config 配置
     */
    public void setConfig(ServerParamConfig config);

    /**
     * 添加中间件
     *
     * @param middleware 中间件
     */
    public void addMiddleware(Middleware middleware);

    /**
     * 添加中间件
     *
     * @param middleware 中间件
     */
    public void addMiddleware(Middleware... middleware);
}
