package top.turboweb.websocket.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;
import top.turboweb.websocket.info.WebSocketConnectInfo;
import top.turboweb.websocket.info.WebSocketConnectInfoContainer;

import java.util.Objects;

/**
 * websocket初始化handler（不可变）。
 * <p>
 * pipeline中独立的后置handler实现：
 * 接收入站的http请求，本handler完成websocket的协议升级：
 * <ul>
 *     <li>websocket升级协议请求（GET方法、Connection头包含upgrade且Upgrade为websocket）且uri匹配路径表达式：
 *     向pipeline追加 {@link WebSocketServerProtocolHandler} 与
 *     {@link WebSocketChannelHandler}，并存储连接信息到 {@link WebSocketConnectInfoContainer}，
 *     完成websocket的协议升级；</li>
 *     <li>其余请求（普通http误转发、其他升级协议、路径不匹配的websocket升级）：本handler不处理，
 *     retain后继续向后转发，由pipeline后续处理器决定。</li>
 * </ul>
 * websocket处理器经建造者模式装配，装配方可注入 {@link MicroTaskExecutor}，
 * 未注入时默认使用非受限虚拟线程微任务执行器。
 * 本处理器标注了 {@link ChannelHandler.Sharable}，无连接级状态，作为单例由
 * {@link top.turboweb.websocket.runtime.WebSocketServerRuntime} 装配并跨channel复用。
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
     * 缺省微任务执行器所建虚拟线程的名称
     */
    private static final String DEFAULT_MICRO_TASK_THREAD_NAME = "turboweb-websocket-handler";

    /**
     * websocket处理的路径表达式
     */
    private final String websocketPath;

    /**
     * 处理websocket的handler
     */
    private final WebSocketChannelHandler webSocketDispatcherHandler;

    private WebSocketInitChannelHandler(Builder builder) {
        this.websocketPath = builder.websocketPath;
        this.webSocketDispatcherHandler = new WebSocketChannelHandler(
                builder.webSocketHandler, builder.microTaskExecutor);
    }

    /**
     * 创建websocket初始化handler构建器，默认使用非受限虚拟线程微任务执行器处理websocket消息
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
     * {@link WebSocketInitChannelHandler} 的建造者，链式方法校验非法参数立即抛出 {@link IllegalArgumentException}。
     */
    public static final class Builder {

        private String websocketPath;

        private WebSocketHandler webSocketHandler;

        /**
         * 消息处理微任务执行器，null表示未配置（构建时缺省创建非受限虚拟线程微任务执行器）
         */
        private MicroTaskExecutor microTaskExecutor = null;

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
            this.webSocketHandler = Objects.requireNonNull(webSocketHandler, "webSocketHandler can not be null");
            return this;
        }

        /**
         * 设置消息处理微任务执行器，websocket消息经其提交执行；执行器的生命周期由传入方管理
         *
         * @param microTaskExecutor 微任务执行器，不能为null
         */
        public Builder microTaskExecutor(MicroTaskExecutor microTaskExecutor) {
            this.microTaskExecutor = Objects.requireNonNull(microTaskExecutor, "microTaskExecutor must not be null");
            return this;
        }

        /**
         * 构建websocket初始化handler，未注入微任务执行器时缺省创建非受限虚拟线程微任务执行器
         */
        public WebSocketInitChannelHandler build() {
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath can not be blank");
            }
            Objects.requireNonNull(webSocketHandler, "webSocketHandler can not be null");
            if (microTaskExecutor == null) {
                microTaskExecutor = VirtualThreadMicroTaskExecutor.ofUnlimited(DEFAULT_MICRO_TASK_THREAD_NAME);
            }
            return new WebSocketInitChannelHandler(this);
        }
    }
}
