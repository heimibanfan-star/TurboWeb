package top.turboweb.core.initializer.factory;

import top.turboweb.core.server.TurboWebServer;
import top.turboweb.websocket.WebSocketHandler;

/**
 * http协议分发器的构造器接口
 */
public interface HttpProtocolDispatcherBuilder {
    /**
     * 设置websocket处理器
     *
     * @param path websocket处理的路径表达式
     * @param webSocketHandler websocket处理器
     */
    HttpProtocolDispatcherBuilder websocket(String path, WebSocketHandler webSocketHandler);

    /**
     * 设置websocket处理器
     *
     * @param path websocket处理路径表达式
     * @param webSocketHandler websocket处理器
     * @param forkJoinThreadNum 工作窃取线程的数量
     */
    HttpProtocolDispatcherBuilder websocket(String path, WebSocketHandler webSocketHandler, int forkJoinThreadNum);


    TurboWebServer and();
}
