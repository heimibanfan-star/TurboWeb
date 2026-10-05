package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.websocket.handler.WebSocketHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Netty处理器管理器（不可变）。
 * <p>
 * 统一管理 {@link HttpProtocolDispatcher} 与 {@link WebSocketInitHandler} 两个处理器：
 * 处理器实例不由外部传入，而是通过建造者的 enableXXX 方法传参启用、由管理器内部创建；
 * 两个处理器均允许为空，未启用的处理器不参与装配，各组合（http+websocket、纯http、
 * 纯websocket、空管理器）均合法。核心职责是管理二者的装配顺序：
 * <ol>
 *     <li>{@link HttpProtocolDispatcher} 在前：处理普通http请求，并把携带Upgrade头的
 *     http升级协议请求向后转发；</li>
 *     <li>{@link WebSocketInitHandler} 在后：接收分发器转发来的握手请求，完成websocket的协议升级。</li>
 * </ol>
 * 二者同时启用时顺序颠倒会导致握手请求无法到达websocket初始化handler，因此装配pipeline
 * 只能经由本管理器完成。两个处理器均为Sharable，管理器实例可跨channel复用，
 * {@link #configure(ChannelPipeline)} 重复调用时按类型去重，不会向pipeline中重复添加。
 * 注意：http编解码等前置handler不归本管理器负责，须由调用方先行装配。
 *
 * <pre>{@code
 * NettyHandlerManager handlerManager = NettyHandlerManager.builder()
 *         .enableHttp(httpScheduler)
 *         .enableWebSocket("/ws/.*", myWebSocketHandler)
 *         .build();
 * // 在ChannelInitializer中按顺序装配到管道
 * handlerManager.configure(pipeline);
 * }</pre>
 */
public class NettyHandlerManager {

    /**
     * http协议分发器，装配顺序第一位，未启用http时为null
     */
    private final HttpProtocolDispatcher httpProtocolDispatcher;

    /**
     * websocket初始化handler，装配顺序第二位，未启用websocket时为null（没有websocket）
     */
    private final WebSocketInitHandler webSocketInitHandler;

    private NettyHandlerManager(Builder builder) {
        this.httpProtocolDispatcher = builder.httpProtocolDispatcher;
        this.webSocketInitHandler = builder.webSocketInitHandler;
    }

    /**
     * 创建处理器管理器构建器，默认两个处理器均未启用
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取http协议分发器，可能为null（未通过 enableHttp 启用）
     */
    public HttpProtocolDispatcher getHttpProtocolDispatcher() {
        return httpProtocolDispatcher;
    }

    /**
     * 获取websocket初始化handler，可能为null（未通过 enableWebSocket 启用）
     */
    public WebSocketInitHandler getWebSocketInitHandler() {
        return webSocketInitHandler;
    }

    /**
     * 是否启用了http协议分发器
     */
    public boolean containsHttp() {
        return httpProtocolDispatcher != null;
    }

    /**
     * 是否启用了websocket初始化handler
     */
    public boolean containsWebSocket() {
        return webSocketInitHandler != null;
    }

    /**
     * 按装配顺序返回已启用的处理器列表：分发器在前，websocket初始化handler在后，未启用的不包含
     */
    public List<ChannelHandler> orderedHandlers() {
        List<ChannelHandler> handlers = new ArrayList<>(2);
        if (httpProtocolDispatcher != null) {
            handlers.add(httpProtocolDispatcher);
        }
        if (webSocketInitHandler != null) {
            handlers.add(webSocketInitHandler);
        }
        return Collections.unmodifiableList(handlers);
    }

    /**
     * 按管理的顺序把已启用的处理器装配到管道尾部，已存在同类型handler时跳过，保证可重复调用
     *
     * @param pipeline netty管道（需已装配http编解码等前置handler）
     */
    public void configure(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline can not be null");
        }
        for (ChannelHandler handler : orderedHandlers()) {
            if (pipeline.get(handler.getClass()) == null) {
                pipeline.addLast(handler);
            }
        }
    }

    /**
     * {@link NettyHandlerManager} 的建造者。
     * 通过 enableXXX 方法传入参数启用处理器并由内部创建实例，外部不直接传递处理器；
     * 未enable的处理器为null，重复enable同一处理器以最后一次设置为准。
     */
    public static final class Builder {

        private HttpProtocolDispatcher httpProtocolDispatcher;

        private WebSocketInitHandler webSocketInitHandler;

        private Builder() {
        }

        /**
         * 启用http协议分发器（装配顺序第一位），内部创建 {@link HttpProtocolDispatcher}
         *
         * @param httpScheduler http调度器，不能为null
         */
        public Builder enableHttp(HttpScheduler httpScheduler) {
            if (httpScheduler == null) {
                throw new IllegalArgumentException("httpScheduler can not be null");
            }
            this.httpProtocolDispatcher = HttpProtocolDispatcher.builder()
                    .httpScheduler(httpScheduler)
                    .build();
            return this;
        }

        /**
         * 启用websocket初始化handler（装配顺序第二位），内部创建 {@link WebSocketInitHandler}，
         * websocket消息默认由虚拟线程处理
         *
         * @param websocketPath    websocket处理的路径表达式，不能为空
         * @param webSocketHandler websocket处理器，不能为null
         */
        public Builder enableWebSocket(String websocketPath, WebSocketHandler webSocketHandler) {
            this.webSocketInitHandler = WebSocketInitHandler.builder()
                    .websocketPath(websocketPath)
                    .webSocketHandler(webSocketHandler)
                    .build();
            return this;
        }

        /**
         * 启用websocket初始化handler（装配顺序第二位），内部创建 {@link WebSocketInitHandler}，
         * websocket消息由工作窃取线程池处理
         *
         * @param websocketPath     websocket处理的路径表达式，不能为空
         * @param webSocketHandler  websocket处理器，不能为null
         * @param forkJoinThreadNum 工作窃取线程池线程数，必须大于0
         */
        public Builder enableWebSocket(String websocketPath, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
            if (forkJoinThreadNum <= 0) {
                throw new IllegalArgumentException("forkJoinThreadNum must be greater than 0");
            }
            this.webSocketInitHandler = WebSocketInitHandler.builder()
                    .websocketPath(websocketPath)
                    .webSocketHandler(webSocketHandler)
                    .forkJoinThreadNum(forkJoinThreadNum)
                    .build();
            return this;
        }

        /**
         * 构建处理器管理器，两个处理器均未启用时为空管理器
         */
        public NettyHandlerManager build() {
            return new NettyHandlerManager(this);
        }
    }
}
