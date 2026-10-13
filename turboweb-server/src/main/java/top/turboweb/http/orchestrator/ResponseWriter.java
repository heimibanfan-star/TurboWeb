package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalConnectSession;

/**
 * 响应写出器接口：负责一类响应对象的写出。
 * <p>
 * 响应对象通过 {@link top.turboweb.http.core.response.ResponseCall} 声明由哪个写出器
 * 负责写出自身；{@link ResponseWriterManager} 按声明类型先查内置表、再查自定义表，
 * 均未命中时落默认写出器兜底。
 * {@link #write} 执行写出动作，并把写出的完成凭据（{@link ChannelFuture}）返回给调用方
 * 挂接后续动作；无写出凭据的写出器（如忽略写出器）返回 {@code null}，调用方应做空值检查。
 * </p>
 */
public interface ResponseWriter {

    /**
     * 注入磁盘IO线程池，供需要调度磁盘读取任务的写出器（文件流响应）使用；
     * 不消费线程池的写出器存储后不使用，装配与登记期可统一调用。
     *
     * @param threadPool 磁盘IO线程池
     */
    void injectThreadPool(ThreadPool threadPool);

    /**
     * 写出响应。
     *
     * @param httpResponse 响应对象
     * @param session      当前连接会话，提供写出通道
     * @return 写出的完成凭据；无凭据时（如忽略写出）为 {@code null}
     */
    ChannelFuture write(HttpResponse httpResponse, InternalConnectSession session);
}
