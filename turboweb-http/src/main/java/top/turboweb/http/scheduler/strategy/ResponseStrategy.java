package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.connect.InternalConnectSession;

import java.nio.charset.StandardCharsets;

/**
 * 响应的策略抽象类
 */
public abstract class ResponseStrategy {

    /**
     * 调用子类的响应处理方法，进行响应的处理
     *
     * @param response 响应对象
     * @param session 连接的会话对象
     * @return 异步监听对象（可能为空，需要避免空指针）
     */
    public ChannelFuture handle(HttpResponse response, InternalConnectSession session) {
        try {
            return doHandle(response, session);
        } catch (Throwable e) {
            // 发送错误响应
            return session.getChannel().writeAndFlush(buildErrResponse(e));
        }
    }

    protected abstract ChannelFuture doHandle(HttpResponse response, InternalConnectSession session);

    /**
     * 构建错误响应
     *
     * @param throwable 错误
     * @return 错误响应
     */
    private HttpResponse buildErrResponse(Throwable throwable) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        String errMsg = "{\"code\":500,\"msg\":\"" + throwable.getMessage() + "\"}";
        response.content().writeBytes(errMsg.getBytes(GlobalConfig.getResponseCharset()));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=" + GlobalConfig.getResponseCharset().name());
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }
}
