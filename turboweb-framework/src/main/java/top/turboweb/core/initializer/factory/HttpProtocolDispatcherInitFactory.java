package top.turboweb.core.initializer.factory;

import io.netty.channel.EventLoopGroup;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.core.initializer.WebSocketHandlerInitializer;
import top.turboweb.core.initializer.impl.DefaultWebSocketHandlerInitializer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.websocket.WebSocketHandler;

/**
 * HTTP协议分发器工厂
 */
public class HttpProtocolDispatcherInitFactory implements HttpProtocolDispatcherBuilder{

    // websocket初始化器
    private final WebSocketHandlerInitializer webSocketHandlerInitializer;
    private final TurboWebServer server;

    {
        webSocketHandlerInitializer = new DefaultWebSocketHandlerInitializer();
    }

    public HttpProtocolDispatcherInitFactory(TurboWebServer server) {
        this.server = server;
    }

    /**
     * 设置websocket处理器
     *
     * @param path websocket处理的路径表达式
     * @param webSocketHandler websocket处理器
     */
    @Override
    public HttpProtocolDispatcherBuilder websocket(String path, WebSocketHandler webSocketHandler) {
        webSocketHandlerInitializer.setWebSocketHandler(path, webSocketHandler);
        return this;
    }

    /**
     * 设置websocket处理器
     *
     * @param path websocket处理路径表达式
     * @param webSocketHandler websocket处理器
     * @param forkJoinThreadNum 工作窃取线程的数量
     */
    @Override
    public HttpProtocolDispatcherBuilder websocket(String path, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
        webSocketHandlerInitializer.setWebSocketHandler(path, webSocketHandler);
        webSocketHandlerInitializer.setForkJoinThreadNum(forkJoinThreadNum);
        return this;
    }

    @Override
    public TurboWebServer and() {
        return server;
    }

    /**
     * 创建Http协议分发器
     *
     * @param httpScheduler http调度器
     * @return Http协议分发器
     */
    public HttpProtocolDispatcher createDispatcher(HttpScheduler httpScheduler, EventLoopGroup group) {
        return new HttpProtocolDispatcher(
                httpScheduler,
                webSocketHandlerInitializer.isUse()? webSocketHandlerInitializer.init() : null,
                webSocketHandlerInitializer.isUse()? webSocketHandlerInitializer.getPath() : null
        );
    }
}
