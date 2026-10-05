package top.turboweb.http.server.handler.group;

import io.netty.channel.ChannelPipeline;
import top.turboweb.http.server.handler.WebSocketInitHandler;
import top.turboweb.websocket.handler.WebSocketHandler;

/**
 * websocket分组（不可变），构造器只接收本分组的可配置参数，不对外暴露处理器实例的传入。
 * <p>
 * 构造参数为websocket处理的路径表达式、websocket处理器与可选的工作窃取线程池线程数，
 * 分组内部据此创建 {@link WebSocketInitHandler} <b>单例</b>（标注 {@link io.netty.channel.ChannelHandler.Sharable}，
 * 跨channel复用）：接收http请求分组中分发器转发来的握手请求，完成websocket的协议升级，
 * 装配位置在分组顺序最后一位（http分发器之后）。管道中已存在同类型处理器时跳过。
 */
public final class WebSocketGroup implements NettyHandlerGroup {

    /**
     * websocket初始化handler（单例，分组构造时按可配置参数内部创建）
     */
    private final WebSocketInitHandler webSocketInitHandler;

    public WebSocketGroup(String websocketPath, WebSocketHandler webSocketHandler) {
        // websocket初始化handler为单例：分组构造时创建一次，跨channel复用，消息默认由虚拟线程处理
        this.webSocketInitHandler = WebSocketInitHandler.builder()
                .websocketPath(websocketPath)
                .webSocketHandler(webSocketHandler)
                .build();
    }

    public WebSocketGroup(String websocketPath, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
        if (forkJoinThreadNum <= 0) {
            throw new IllegalArgumentException("forkJoinThreadNum must be greater than 0");
        }
        // websocket初始化handler为单例：分组构造时创建一次，跨channel复用，消息由工作窃取线程池处理
        this.webSocketInitHandler = WebSocketInitHandler.builder()
                .websocketPath(websocketPath)
                .webSocketHandler(webSocketHandler)
                .forkJoinThreadNum(forkJoinThreadNum)
                .build();
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        // websocket初始化handler：单例复用，装配在分发器之后
        if (pipeline.get(WebSocketInitHandler.class) == null) {
            pipeline.addLast(webSocketInitHandler);
        }
    }
}
