package org.turbo.core.server;

import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * 服务器接口
 */
public interface TurboServer {

    /**
     * 设置最大内容长度
     *
     * @param maxContentLength 最大内容长度
     */
    public void setMaxContentLength(int maxContentLength);

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
}
