package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.connection.SequencedRequest;
import top.turboweb.http.orchestrator.RequestOrchestrator;

import java.util.Objects;

/**
 * http请求调度处理器（纯http版本，不可变）。
 * <p>
 * 只接收上游定序处理器封章的 {@link SequencedRequest}：以封装携带的序列号建立
 * 一请求一连接（连接写出的每条消息以该序列号封章，供响应定序），原始请求交给
 * {@link RequestOrchestrator} 编排执行，返回后归还收到的请求引用。
 * 未封章的裸请求（如升级协议握手）不经本处理器，自动透传由后续处理器决定。
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
public class HttpRequestSchedulerChannelHandler extends SimpleChannelInboundHandler<SequencedRequest> {

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
    protected void channelRead0(ChannelHandlerContext ctx, SequencedRequest sequencedRequest) throws Exception {
        FullHttpRequest fullHttpRequest = sequencedRequest.request();
        // 以封装携带的序列号建立一请求一连接
        InternalHttpConnection connectSession = new InternalHttpConnection(ctx.channel(), sequencedRequest.sequence());
        try {
            // 请求引用由编排器在提交前自行持有，本处理器返回后归还收到的引用
            requestOrchestrator.orchestrate(fullHttpRequest, connectSession);
        } finally {
            fullHttpRequest.release();
        }
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
