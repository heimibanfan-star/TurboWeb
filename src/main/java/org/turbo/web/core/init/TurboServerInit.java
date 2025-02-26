package org.turbo.web.core.init;

import io.netty.bootstrap.ServerBootstrap;

/**
 * 服务器初始化接口
 */
public interface TurboServerInit {

    /**
     * 在服务器初始化之前调用
     *
     * @param serverBootstrap 服务器启动引导类
     */
    void beforeTurboServerInit(ServerBootstrap serverBootstrap);

    /**
     * 在服务器初始化之后调用
     *
     * @param serverBootstrap 服务器启动引导类
     */
    void afterTurboServerInit(ServerBootstrap serverBootstrap);

    /**
     * 在服务器启动之后调用
     */
    void afterTurboServerStart();
}
