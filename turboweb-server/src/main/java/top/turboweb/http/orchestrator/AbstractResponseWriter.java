package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.utils.base.ErrorStrGenerator;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalConnectSession;

/**
 * 响应写出器抽象基类：封装统一的异常处理与线程池注入。
 * <p>
 * {@link #write} 为模板方法：捕获写出过程中抛出的任何异常，
 * 自动构建 HTML 格式的 500 错误页面响应给客户端，避免连接泄漏；
 * 子类只需实现 {@link #doWrite} 关注自身响应类型的写出逻辑，
 * 无须关心异常处理或通用错误响应。
 * </p>
 * <p>
 * 磁盘IO线程池经 {@link #injectThreadPool} 统一存储于本基类字段，
 * 仅文件流类写出器在写出时消费它，其余写出器忽略即可。
 * </p>
 */
public abstract class AbstractResponseWriter implements ResponseWriter {

    /** 磁盘IO线程池，装配与登记期经 injectThreadPool 注入；本写出器不负责其生命周期。 */
    protected ThreadPool threadPool;

    @Override
    public void injectThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * 执行响应写出的模板方法。
     * <p>
     * 此方法会捕获所有子类写出中的异常，并自动发送 500 错误响应；
     * 子类的写出结果（可能为 {@code null}）原样返回。
     *
     * @param httpResponse 响应对象
     * @param session      当前请求关联的连接会话
     * @return 异步操作结果（可能为空，调用方应进行空指针检查）
     */
    @Override
    public ChannelFuture write(HttpResponse httpResponse, InternalConnectSession session) {
        try {
            return doWrite(httpResponse, session);
        } catch (Throwable e) {
            // 发送错误响应
            return session.getChannel().writeAndFlush(buildErrResponse(e));
        }
    }

    /**
     * 执行具体响应写出逻辑的抽象方法。
     * <p>
     * 子类应在此方法中实现响应数据的写入与发送逻辑，
     * 并根据异步结果返回 {@link ChannelFuture}。
     * </p>
     *
     * @param response 响应对象
     * @param session  当前连接会话
     * @return 响应写入的异步监听对象
     */
    protected abstract ChannelFuture doWrite(HttpResponse response, InternalConnectSession session);

    /**
     * 构建一个 500 错误响应。
     * <p>
     * 当 {@link #write} 捕获异常时，将调用此方法生成标准 HTML 错误页面返回客户端。
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
