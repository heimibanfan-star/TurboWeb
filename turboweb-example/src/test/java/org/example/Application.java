package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.example.controller.UserController;
import org.example.middleware.TestMiddleware;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.router.RouterManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws IOException, InterruptedException {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .middleware(new TestMiddleware())
                .routerManager(routerManager)
                .and()
                .start(8080);
    }
}
