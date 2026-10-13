package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalConnectSession;

/**
 * 响应写出器抽象基类：封装统一的异常处理与线程池注入。
 * <p>
 * {@link #write} 为模板方法：捕获写出过程中抛出的任何异常——写出异常不可预知且无法正常恢复，
 * 记录错误日志后直接关闭连接，本次写出以不可监听结果返回；
 * 子类只需实现 {@link #doWrite} 关注自身响应类型的写出逻辑，无须关心异常处理。
 * </p>
 * <p>
 * 磁盘IO线程池经 {@link #injectThreadPool} 统一存储于本基类字段，
 * 仅文件流类写出器在写出时消费它，其余写出器忽略即可。
 * </p>
 */
public abstract class AbstractResponseWriter implements ResponseWriter {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(AbstractResponseWriter.class);

    /** 磁盘IO线程池，构造期经 injectThreadPool 注入；本写出器不负责其生命周期。 */
    protected ThreadPool threadPool;

    @Override
    public void injectThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * 执行响应写出的模板方法。
     * <p>
     * 此方法会捕获所有子类写出中的异常：写出异常不可预知且无法正常恢复，
     * 记录日志并关闭连接后返回不可监听结果；
     * 子类返回的写出凭据非空时翻译为可监听结果，为空（无凭据写出）翻译为不可监听结果。
     *
     * @param httpResponse 响应对象
     * @param session      当前请求关联的连接会话
     * @return 写出结果，永不为null；是否可监听凭 {@link WriteResult#writeType()} 判断，无须空值检查
     */
    @Override
    public WriteResult write(HttpResponse httpResponse, InternalConnectSession session) {
        try {
            ChannelFuture future = doWrite(httpResponse, session);
            return future == null ? WriteResult.notListenable() : WriteResult.listenable(future);
        } catch (Throwable e) {
            // 写出异常不可预知且无法恢复: 记录日志并关闭连接, 本次写出无凭据可监听
            log.error("响应写出异常, 关闭连接", e);
            session.close();
            return WriteResult.notListenable();
        }
    }

    /**
     * 执行具体响应写出逻辑的抽象方法。
     * <p>
     * 子类应在此方法中实现响应数据的写入与发送逻辑，
     * 并根据异步结果返回 {@link ChannelFuture}；
     * 无写出凭的场景（如忽略写出、发射器仅初始化）返回 {@code null}，
     * 由 {@link #write} 模板统一翻译为不可监听结果。
     * </p>
     *
     * @param response 响应对象
     * @param session  当前连接会话
     * @return 响应写入的异步监听对象；无凭据时为 {@code null}
     */
    protected abstract ChannelFuture doWrite(HttpResponse response, InternalConnectSession session);
}
