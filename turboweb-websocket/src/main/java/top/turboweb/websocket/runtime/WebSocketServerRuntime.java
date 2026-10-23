package top.turboweb.websocket.runtime;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.common.utils.resource.ScopedResource;
import top.turboweb.server.runtime.ServerRuntime;
import top.turboweb.websocket.handler.WebSocketHandler;
import top.turboweb.websocket.handler.WebSocketInitChannelHandler;

import java.util.Objects;

/**
 * websocket应用，服务器与业务装配逻辑之间的桥梁，参考 top.turboweb.http.server.runtime.HttpServerRuntime 设计。
 * <p>
 * 对外只暴露 {@link #configure(ChannelPipeline)}：每条新连接建立时向pipeline尾部追加
 * websocket初始化处理器（无状态单例，跨连接复用；握手时由它动态追加协议处理器与
 * websocket分发处理器，连接级处理器由初始化handler在升级时自行装配）；
 * 对内通过 {@link Builder} 在构建期一次性完成路径表达式、业务处理器与消息处理线程模型的校验与装配。
 * <p>
 * 本实例装配完成后不再持有可变状态，可被多个worker线程并发调用。
 * 注册时须排在http应用之后（接收 top.turboweb.http.server.handler.HttpRequestSchedulerChannelHandler
 * 向后转发的websocket升级协议请求）。
 * <p>
 * 装配期创建的可关闭资源不在本类中逐个持有，而是统一登记进本应用持有的
 * {@link ScopedResource} 资源作用域：初始化handler的销毁适配器（委托分发处理器释放消息处理线程池；
 * 工作窃取模式下关闭本应用私有的ForkJoinPool，默认的虚拟线程模式复用JVM全局共享池不做关闭）
 * 登记于此，关闭时由本类 {@link #close()} 统一释放，
 * 服务器关闭时会统一触发，调用方也可对应用实例自行try-with-resources。
 *
 * <pre>{@code
 * WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
 *         .path("/ws/.*")
 *         .handler(myWebSocketHandler)
 *         .build();
 * }</pre>
 */
public class WebSocketServerRuntime implements ServerRuntime {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(WebSocketServerRuntime.class);

    /**
     * websocket初始化handler，构建期装配好的无状态单例（{@code @Sharable}），
     * 负责接收升级协议请求完成websocket协议升级，内部持有websocket分发处理器
     */
    private final WebSocketInitChannelHandler webSocketInitHandler;

    /**
     * 应用的资源作用域：装配期由 {@link Builder#build()} 登记的初始化handler销毁适配器在此，
     * 关闭时由 {@link #close()} 统一释放，本类无须持有个别资源的引用
     */
    private final ScopedResource scopedResource;

    public WebSocketServerRuntime(WebSocketInitChannelHandler webSocketInitHandler, ScopedResource scopedResource) {
        this.webSocketInitHandler = Objects.requireNonNull(webSocketInitHandler, "The websocket init handler must not be null");
        this.scopedResource = Objects.requireNonNull(scopedResource, "The resource scope must not be null");
    }

    /**
     * 获取本应用持有的资源作用域：装配期登记的可关闭资源在此，关闭时由 {@link #close()} 统一释放
     */
    public ScopedResource getScopedResource() {
        return this.scopedResource;
    }

    /**
     * 关闭应用：释放 {@link #getScopedResource()} 中登记的全部资源（初始化handler的销毁适配器），
     * 重复调用为空操作；单个资源释放失败只记录日志，不中断其余释放
     */
    @Override
    public void close() {
        this.scopedResource.close();
    }

    /**
     * 每条新连接建立时被调用一次，向pipeline尾部追加应用级处理器。
     * 初始化handler为单例直接复用，握手匹配后的连接级处理器由其动态装配
     */
    @Override
    public void configure(ChannelPipeline pipeline) {
        pipeline.addLast(webSocketInitHandler);
        log.debug("Websocket runtime handlers assembled for connection [{}]",
                pipeline.channel().id().asShortText());
    }

    /**
     * {@link WebSocketServerRuntime} 的建造者：持有应用装配所需的全部组件对象
     * （websocket路径表达式与业务处理器），纯参数配置（消息处理线程模型）为可选项，
     * 默认使用虚拟线程处理websocket消息，链式方法校验非法参数立即抛出
     * {@link IllegalArgumentException}。
     * <p>
     * {@link #build()} 完成校验后按线程模型创建 {@link WebSocketInitChannelHandler} 单例，
     * 把它的销毁适配器登记进新建的资源作用域，一并装配进应用实例。
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
            this.webSocketHandler = Objects.requireNonNull(webSocketHandler, "webSocketHandler must not be null");
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
         * {@link WebSocketInitChannelHandler} 单例（未设置线程数时使用虚拟线程），
         * 将其销毁适配器登记进新建的资源作用域后一并装配进应用实例
         */
        public WebSocketServerRuntime build() {
            log.info("Assembling websocket runtime...");
            if (websocketPath == null || websocketPath.isBlank()) {
                throw new IllegalArgumentException("websocketPath can not be blank");
            }
            Objects.requireNonNull(webSocketHandler, "webSocketHandler can not be null");
            WebSocketInitChannelHandler.Builder initHandlerBuilder = WebSocketInitChannelHandler.builder()
                    .websocketPath(websocketPath)
                    .webSocketHandler(webSocketHandler);
            if (forkJoinThreadNum > 0) {
                initHandlerBuilder.forkJoinThreadNum(forkJoinThreadNum);
                log.info("Websocket message thread model: ForkJoinPool with {} threads", forkJoinThreadNum);
            } else {
                log.info("Websocket message thread model: virtual threads");
            }
            WebSocketInitChannelHandler webSocketInitHandler = initHandlerBuilder.build();
            // 初始化handler的销毁适配器登记进应用的资源作用域，应用关闭时释放分发处理器的线程池
            ScopedResource scopedResource = new ScopedResource();
            scopedResource.register(() -> {
                webSocketInitHandler.destroy();
                log.info("Websocket runtime closed");
            });
            log.info("Websocket runtime assembled, path pattern {}", websocketPath);
            return new WebSocketServerRuntime(webSocketInitHandler, scopedResource);
        }

    }
}
