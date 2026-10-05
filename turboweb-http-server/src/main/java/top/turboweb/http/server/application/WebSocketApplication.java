package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.server.handler.WebSocketInitHandler;
import top.turboweb.websocket.handler.WebSocketHandler;

import java.util.Objects;

/**
 * websocket应用，服务器与业务装配逻辑之间的桥梁，参考 {@link HttpRequestApplication} 设计。
 * <p>
 * 对外只暴露 {@link #configure(ChannelPipeline)}：每条新连接建立时向pipeline尾部追加
 * websocket初始化处理器（无状态单例，跨连接复用；握手时由它动态追加协议处理器与
 * websocket分发处理器，连接级处理器由初始化handler在升级时自行装配）；
 * 对内通过 {@link Builder} 在构建期一次性完成路径表达式、业务处理器与消息处理线程模型的校验与装配。
 * <p>
 * 本实例装配完成后不再持有可变状态，可被多个worker线程并发调用。
 * 装配位置在http应用之后（接收 {@link top.turboweb.http.server.handler.HttpProtocolDispatcher}
 * 向后转发的websocket升级协议请求），与 {@link top.turboweb.http.server.handler.group.WebSocketGroup}
 * 的分组装配顺序约定一致。
 *
 * <pre>{@code
 * WebSocketApplication application = new WebSocketApplication.Builder()
 *         .path("/ws/.*")
 *         .handler(myWebSocketHandler)
 *         .build();
 * }</pre>
 */
public class WebSocketApplication implements Application {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(WebSocketApplication.class);

    /**
     * websocket初始化handler，构建期装配好的无状态单例（{@code @Sharable}），
     * 负责接收升级协议请求完成websocket协议升级，内部持有websocket分发处理器
     */
    private final WebSocketInitHandler webSocketInitHandler;

    public WebSocketApplication(WebSocketInitHandler webSocketInitHandler) {
        this.webSocketInitHandler = Objects.requireNonNull(webSocketInitHandler, "websocket初始化handler不能为null");
    }

    /**
     * 每条新连接建立时被调用一次，向pipeline尾部追加应用级处理器。
     * 初始化handler为单例直接复用，握手匹配后的连接级处理器由其动态装配
     */
    @Override
    public void configure(ChannelPipeline pipeline) {
        pipeline.addLast(webSocketInitHandler);
        log.debug("连接[{}]装配websocket应用处理器完成",
                pipeline.channel().id().asShortText());
    }

    /**
     * {@link WebSocketApplication} 的建造者：持有应用装配所需的全部组件对象
     * （websocket路径表达式与业务处理器），纯参数配置（消息处理线程模型）为可选项，
     * 默认使用虚拟线程处理websocket消息，链式方法校验非法参数立即抛出
     * {@link IllegalArgumentException}。
     * <p>
     * {@link #build()} 完成校验后按线程模型创建 {@link WebSocketInitHandler} 单例，
     * 装配进应用实例。
     */
    public static class Builder {

        /**
         * websocket处理的路径表达式，不能为空
         */
        private String websocketPath = null;
        /**
         * websocket业务处理器，不能为null
         */
        private WebSocketHandler webSocketHandler = null;
        /**
         * 工作窃取线程池的线程数，默认0表示不启用（websocket消息由虚拟线程处理）
         */
        private int forkJoinThreadNum = 0;

        /**
         * 设置websocket处理的路径表达式（uri按正则匹配），为空立即抛出 {@link IllegalArgumentException}
         */
        public Builder path(String websocketPath) {
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath can not be blank");
            }
            this.websocketPath = websocketPath;
            return this;
        }

        /**
         * 设置websocket业务处理器（onOpen/onMessage/onClose回调），为null立即抛出
         * {@link NullPointerException}
         */
        public Builder handler(WebSocketHandler webSocketHandler) {
            this.webSocketHandler = Objects.requireNonNull(webSocketHandler, "webSocketHandler不能为null");
            return this;
        }

        /**
         * 设置工作窃取线程池的线程数，启用后websocket消息由ForkJoinPool处理；
         * 必须大于0，否则立即抛出 {@link IllegalArgumentException}
         */
        public Builder forkJoinThreadNum(int forkJoinThreadNum) {
            if (forkJoinThreadNum <= 0) {
                throw new IllegalArgumentException("forkJoinThreadNum must be greater than 0");
            }
            this.forkJoinThreadNum = forkJoinThreadNum;
            return this;
        }

        /**
         * 构建应用：校验路径表达式与业务处理器均已配置，按线程模型创建
         * {@link WebSocketInitHandler} 单例（未设置线程数时使用虚拟线程）并装配进应用实例
         */
        public WebSocketApplication build() {
            log.info("开始装配websocket应用...");
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath不能为空");
            }
            if (webSocketHandler == null) {
                throw new IllegalArgumentException("webSocketHandler不能为空");
            }
            WebSocketInitHandler.Builder initHandlerBuilder = WebSocketInitHandler.builder()
                    .websocketPath(websocketPath)
                    .webSocketHandler(webSocketHandler);
            if (forkJoinThreadNum > 0) {
                initHandlerBuilder.forkJoinThreadNum(forkJoinThreadNum);
                log.info("websocket消息处理线程模型：ForkJoinPool，线程数 {}", forkJoinThreadNum);
            } else {
                log.info("websocket消息处理线程模型：虚拟线程");
            }
            WebSocketInitHandler webSocketInitHandler = initHandlerBuilder.build();
            log.info("websocket应用装配完成，路径表达式 {}", websocketPath);
            return new WebSocketApplication(webSocketInitHandler);
        }

    }
}
