package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.utils.base.ErrorStrGenerator;
import top.turboweb.http.connect.InternalConnectSession;

import java.nio.charset.StandardCharsets;

/**
 * <p><b>响应策略抽象类。</b></p>
 *
 * <p>
 * 所有响应类型的输出逻辑（例如 {@code FullHttpResponse}、{@code ReactorResponse}、{@code FileResponse} 等）
 * 均应继承此类并实现 {@link #doHandle(HttpResponse, InternalConnectSession)} 方法。
 * </p>
 *
 * <p>
 * 该类封装了统一的异常处理机制：如果响应过程中抛出任何异常，将自动构建
 * 一个 HTML 格式的 500 错误页面响应给客户端，避免连接泄漏。
 * </p>
 *
 * <p>
 * 子类需关注自身逻辑的响应写入实现，而无需关心异常处理或通用错误响应。
 * </p>
 */
public abstract class ResponseStrategy {

    /**
     * 执行响应处理的模板方法。
     * <p>
     * 此方法会捕获所有子类处理中的异常，并自动发送 500 错误响应。
     * 若子类实现未返回 {@link ChannelFuture}，应确保不会导致调用链中断。
     * </p>
     *
     * @param response 响应对象（可为 {@link FullHttpResponse}、{@link HttpResponse} 等类型）
     * @param session  当前请求关联的连接会话
     * @return 异步操作结果（可能为空，调用方应进行空指针检查）
     */
    public ChannelFuture handle(HttpResponse response, InternalConnectSession session) {
        try {
            return doHandle(response, session);
        } catch (Throwable e) {
            // 发送错误响应
            return session.getChannel().writeAndFlush(buildErrResponse(e));
        }
    }

    /**
     * 执行具体响应处理逻辑的抽象方法。
     * <p>
     * 子类应在此方法中实现响应数据的写入与发送逻辑，
     * 并根据异步结果返回 {@link ChannelFuture}。
     * </p>
     *
     * @param response 响应对象
     * @param session  当前连接会话
     * @return 响应写入的异步监听对象
     */
    protected abstract ChannelFuture doHandle(HttpResponse response, InternalConnectSession session);

    /**
     * 构建一个 500 错误响应。
     * <p>
     * 当 {@link #handle(HttpResponse, InternalConnectSession)} 捕获异常时，
     * 将调用此方法生成标准 HTML 错误页面返回客户端。
     * </p>
     *
     * @param throwable 抛出的异常对象
     * @return 带有错误信息的 HTTP 响应对象
     */
    private HttpResponse buildErrResponse(Throwable throwable) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        String errMsg = ErrorStrGenerator.errHtml(500, throwable.getMessage());
        response.content().writeBytes(errMsg.getBytes(GlobalConfig.getResponseCharset()));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=" + GlobalConfig.getResponseCharset().name());
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }
}
