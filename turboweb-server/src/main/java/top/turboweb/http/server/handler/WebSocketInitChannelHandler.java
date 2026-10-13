package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import top.turboweb.websocket.dispatch.WebSocketDispatcherChannelHandler;
import top.turboweb.websocket.handler.WebSocketHandler;
import top.turboweb.websocket.info.WebSocketConnectInfo;
import top.turboweb.websocket.info.WebSocketConnectInfoContainer;

/**
 * websocket初始化handler（不可变）。
 * <p>
 * 参考 top.turboweb.websocket.init.PathWebSocketPreInit 与 top.turboweb.core.dispatch.HttpProtocolDispatcher
 * 中的handleInitWebSocket逻辑，改为pipeline中独立的后置handler实现：
 * 与纯http的 {@link HttpRequestSchedulerChannelHandler} 配套使用，处理器把携带Upgrade头的http升级协议请求向后转发，
 * 由本handler完成websocket的协议升级：
 * <ul>
 *     <li>websocket升级协议请求（GET方法、Connection头包含upgrade且Upgrade为websocket）且uri匹配路径表达式：
 *     向pipeline追加 {@link WebSocketServerProtocolHandler} 与
 *     {@link WebSocketDispatcherChannelHandler}，并存储连接信息到 {@link WebSocketConnectInfoContainer}，
 *     完成websocket的协议升级；</li>
 *     <li>其余请求（普通http误转发、其他升级协议、路径不匹配的websocket升级）：本handler不处理，
 *     retain后继续向后转发，由pipeline后续处理器决定。</li>
 * </ul>
 * websocket处理器的创建参考 top.turboweb.core.initializer.impl.DefaultWebSocketHandlerInitializer
 * 与 top.turboweb.core.initializer.factory.HttpProtocolDispatcherInitFactory，改为建造者模式，
 * 默认使用虚拟线程，可选工作窃取线程池。
 * 本处理器标注了 {@link ChannelHandler.Sharable}，无连接级状态，作为单例由
 * {@link top.turboweb.http.server.application.WebSocketApplication} 装配并跨channel复用。
 *
 * <pre>{@code
 * WebSocketInitChannelHandler webSocketInitHandler = WebSocketInitChannelHandler.builder()
 *         .websocketPath("/ws/.*")
 *         .webSocketHandler(myWebSocketHandler)
 *         .build();
 * }</pre>
 */
@ChannelHandler.Sharable
public class WebSocketInitChannelHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * websocket处理的路径表达式
     */
    private final String websocketPath;

    /**
     * 处理websocket的handler
     */
    private final WebSocketDispatcherChannelHandler webSocketDispatcherHandler;

    private WebSocketInitChannelHandler(Builder builder) {
        this.websocketPath = builder.websocketPath;
        if (builder.useForkJoin) {
            this.webSocketDispatcherHandler = new WebSocketDispatcherChannelHandler(
                    builder.webSocketHandler, true, builder.forkJoinThreadNum);
        } else {
            this.webSocketDispatcherHandler = new WebSocketDispatcherChannelHandler(builder.webSocketHandler);
        }
    }

    /**
     * 创建websocket初始化handler构建器，默认使用虚拟线程处理websocket消息
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
        // 再次判断：只有websocket升级协议请求且路径匹配，才由本handler完成协议升级的初始化
        if (isWebSocketUpgrade(fullHttpRequest) && fullHttpRequest.uri().matches(websocketPath)) {
            handleInitWebSocket(channelHandlerContext, fullHttpRequest);
        }
        // 增加引用，防止被当前处理器给释放内存
        fullHttpRequest.retain();
        // 继续向后转发（匹配的握手请求由新追加的协议处理器接收，其余交由后续处理器决定）
        channelHandlerContext.fireChannelRead(fullHttpRequest);
    }

    /**
     * 判断是否为websocket升级协议请求：GET方法，Connection头包含upgrade，且Upgrade头的值为websocket
     *
     * @param request http请求
     */
    private static boolean isWebSocketUpgrade(FullHttpRequest request) {
        if (!HttpMethod.GET.equals(request.method())) {
            return false;
        }
        HttpHeaders headers = request.headers();
        if (!headers.contains(HttpHeaderNames.UPGRADE, "websocket", true)) {
            return false;
        }
        // Connection头可能是逗号分隔的多值（如 keep-alive, Upgrade），做忽略大小写的包含判断
        String connection = headers.get(HttpHeaderNames.CONNECTION);
        return connection != null && connection.toLowerCase().contains("upgrade");
    }

    /**
     * 初始化websocket：改写pipeline并存储连接信息
     *
     * @param ctx     管道上下文
     * @param request 握手请求
     */
    private void handleInitWebSocket(ChannelHandlerContext ctx, FullHttpRequest request) {
        ChannelPipeline pipeline = ctx.pipeline();
        String uri = request.uri();
        if (pipeline.get(WebSocketServerProtocolHandler.class) == null) {
            pipeline.addLast(new WebSocketServerProtocolHandler(uri) {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                        webSocketDispatcherHandler.noticeFinishShakeHand(ctx);
                    }
                    super.userEventTriggered(ctx, evt);
                }
            });
            pipeline.addLast(webSocketDispatcherHandler);
        }
        // 存储websocket的连接信息
        String channelId = ctx.channel().id().asLongText();
        WebSocketConnectInfo connectInfo = new WebSocketConnectInfo(uri, request.headers());
        WebSocketConnectInfoContainer.putWebSocketConnectInfo(channelId, connectInfo);
    }

    /**
     * 销毁本handler持有的执行资源：委托内部的 {@link WebSocketDispatcherChannelHandler} 关闭其私有的
     * 消息处理线程池（虚拟线程模式下使用全局共享池，不会被关闭）。由
     * {@link top.turboweb.http.server.application.WebSocketApplication#close()} 发起，重复调用为幂等操作。
     */
    public void destroy() {
        webSocketDispatcherHandler.destroy();
    }

    /**
     * {@link WebSocketInitChannelHandler} 的建造者，链式方法校验非法参数立即抛出 {@link IllegalArgumentException}。
     */
    public static final class Builder {

        private String websocketPath;

        private WebSocketHandler webSocketHandler;

        /**
         * 是否启用工作窃取线程池，默认不启用（使用虚拟线程）
         */
        private boolean useForkJoin = false;

        /**
         * 工作窃取线程池的线程数
         */
        private int forkJoinThreadNum = 0;

        private Builder() {
        }

        /**
         * 设置websocket处理的路径表达式
         *
         * @param websocketPath 路径表达式，不能为空
         */
        public Builder websocketPath(String websocketPath) {
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath can not be blank");
            }
            this.websocketPath = websocketPath;
            return this;
        }

        /**
         * 设置websocket处理器
         *
         * @param webSocketHandler websocket处理器，不能为null
         */
        public Builder webSocketHandler(WebSocketHandler webSocketHandler) {
            if (webSocketHandler == null) {
                throw new IllegalArgumentException("webSocketHandler can not be null");
            }
            this.webSocketHandler = webSocketHandler;
            return this;
        }

        /**
         * 设置工作窃取线程池的线程数，启用后websocket消息由ForkJoinPool处理
         *
         * @param forkJoinThreadNum 线程数，必须大于0
         */
        public Builder forkJoinThreadNum(int forkJoinThreadNum) {
            if (forkJoinThreadNum <= 0) {
                throw new IllegalArgumentException("forkJoinThreadNum must be greater than 0");
            }
            this.forkJoinThreadNum = forkJoinThreadNum;
            this.useForkJoin = true;
            return this;
        }

        /**
         * 构建websocket初始化handler
         */
        public WebSocketInitChannelHandler build() {
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath不能为空");
            }
            if (webSocketHandler == null) {
                throw new IllegalArgumentException("webSocketHandler不能为空");
            }
            return new WebSocketInitChannelHandler(this);
        }
    }
}
