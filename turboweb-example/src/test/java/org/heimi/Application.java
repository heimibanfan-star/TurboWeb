package org.heimi;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.CoreNettyServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

/**
 * TODO
 */
public class Application {

    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(1)
                .http().routerManager(routerManager)
                .and()
                .configServer(c -> {
                    c.setShowRequestLog(false);
                }).start(8080);
    }
}
