package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.server.handler.manager.NettyHandlerManager;

/**
 * http协议分发器（纯http版本，不可变）。
 * <p>
 * 参考 top.turboweb.core.dispatch.HttpProtocolDispatcher 实现，区别在于本分发器内部
 * 不聚合任何websocket相关的处理器，只专注于http协议的处理：
 * <ul>
 *     <li>普通http请求：封装会话对象后交给 {@link HttpScheduler} 调度执行；</li>
 *     <li>http升级协议请求（携带Upgrade头，如websocket握手）：不在本分发器处理，
 *     retain后继续向后转发，由pipeline中后续的处理器完成协议升级。</li>
 * </ul>
 * 创建方式参考 top.turboweb.core.initializer.factory.HttpProtocolDispatcherInitFactory，
 * 改为建造者模式，默认没有websocket。
 * 本处理器标注了 {@link ChannelHandler.Sharable}，无连接级状态，作为单例由
 * {@link NettyHandlerManager} 装配并跨channel复用。
 *
 * <pre>{@code
 * HttpProtocolDispatcher dispatcher = HttpProtocolDispatcher.builder()
 *         .httpScheduler(httpScheduler)
 *         .build();
 * }</pre>
 */
@ChannelHandler.Sharable
public class HttpProtocolDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * http调度器
     */
    private final HttpScheduler httpScheduler;

    private HttpProtocolDispatcher(Builder builder) {
        this.httpScheduler = builder.httpScheduler;
    }

    /**
     * 创建分发器构建器，默认不聚合websocket处理器
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
        // 增加引用，防止被当前处理器给释放内存
        fullHttpRequest.retain();
        // 封装连接的会话对象
        InternalConnectSession connectSession = new InternalConnectSession(channelHandlerContext.channel());
        // 执行异步任务
        httpScheduler.execute(fullHttpRequest, connectSession);
    }

    /**
     * {@link HttpProtocolDispatcher} 的建造者，链式方法校验非法参数立即抛出 {@link IllegalArgumentException}。
     */
    public static final class Builder {

        private HttpScheduler httpScheduler;

        private Builder() {
        }

        /**
         * 设置http调度器
         *
         * @param httpScheduler http调度器，不能为null
         */
        public Builder httpScheduler(HttpScheduler httpScheduler) {
            if (httpScheduler == null) {
                throw new IllegalArgumentException("httpScheduler can not be null");
            }
            this.httpScheduler = httpScheduler;
            return this;
        }

        /**
         * 构建分发器对象，只处理http协议，默认没有websocket
         */
        public HttpProtocolDispatcher build() {
            if (httpScheduler == null) {
                throw new IllegalArgumentException("httpScheduler不能为空");
            }
            return new HttpProtocolDispatcher(this);
        }
    }
}
