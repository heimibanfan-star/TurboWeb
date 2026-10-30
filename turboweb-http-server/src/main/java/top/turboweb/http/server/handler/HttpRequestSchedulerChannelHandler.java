package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.orchestrator.RequestOrchestrator;

import java.util.Objects;

/**
 * http请求调度处理器（纯http版本，不可变）。
 * <p>
 * 参考 top.turboweb.core.dispatch.HttpProtocolDispatcher 实现，区别在于本处理器内部
 * 不聚合任何websocket相关的处理器，只专注于把http请求送进请求编排器：
 * <ul>
 *     <li>普通http请求：封装连接会话对象后交给 {@link RequestOrchestrator} 编排执行；</li>
 *     <li>http升级协议请求（携带Upgrade头，如websocket握手）：不在本处理器处理，
 *     retain后继续向后转发，由pipeline中后续的处理器完成协议升级。</li>
 * </ul>
 * 创建方式参考 top.turboweb.core.initializer.factory.HttpProtocolDispatcherInitFactory，
 * 改为建造者模式，默认没有websocket。
 * 本处理器标注了 {@link ChannelHandler.Sharable}，无连接级状态，作为单例由
 * {@link top.turboweb.http.server.runtime.HttpServerRuntime} 装配并跨channel复用。
 *
 * <pre>{@code
 * HttpRequestSchedulerChannelHandler schedulerHandler = HttpRequestSchedulerChannelHandler.builder()
 *         .orchestrator(requestOrchestrator)
 *         .build();
 * }</pre>
 */
@ChannelHandler.Sharable
public class HttpRequestSchedulerChannelHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * 请求编排器
     */
    private final RequestOrchestrator requestOrchestrator;

    private HttpRequestSchedulerChannelHandler(Builder builder) {
        this.requestOrchestrator = builder.requestOrchestrator;
    }

    /**
     * 创建处理器构建器，默认不聚合websocket处理器
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
        // http升级协议请求（携带Upgrade头），本分发器不处理，继续向后转发
        if (fullHttpRequest.headers().contains(HttpHeaderNames.UPGRADE)) {
            // 增加引用，防止被当前处理器给释放内存
            fullHttpRequest.retain();
            channelHandlerContext.fireChannelRead(fullHttpRequest);
            return;
        }
        // 封装连接的会话对象
        InternalHttpConnection connectSession = new InternalHttpConnection(channelHandlerContext.channel());
        // 请求引用由编排器在提交前自行持有，本处理器返回后由基类归还本处理器的引用
        requestOrchestrator.orchestrate(fullHttpRequest, connectSession);
    }

    /**
     * {@link HttpRequestSchedulerChannelHandler} 的建造者，链式方法校验参数为null立即抛出 {@link NullPointerException}。
     */
    public static final class Builder {

        private RequestOrchestrator requestOrchestrator;

        private Builder() {
        }

        /**
         * 设置请求编排器
         *
         * @param requestOrchestrator 请求编排器，不能为null
         */
        public Builder orchestrator(RequestOrchestrator requestOrchestrator) {
            this.requestOrchestrator = Objects.requireNonNull(requestOrchestrator, "requestOrchestrator can not be null");
            return this;
        }

        /**
         * 构建处理器对象，只处理http请求，默认没有websocket
         */
        public HttpRequestSchedulerChannelHandler build() {
            Objects.requireNonNull(requestOrchestrator, "requestOrchestrator can not be null");
            return new HttpRequestSchedulerChannelHandler(this);
        }
    }
}
