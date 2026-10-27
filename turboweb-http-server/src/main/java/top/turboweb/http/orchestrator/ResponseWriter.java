package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalHttpConnection;

import java.util.Objects;

/**
 * 响应写出器接口：负责一类响应对象的写出。
 * <p>
 * 响应对象通过 {@link top.turboweb.http.core.response.ResponseCall} 声明由哪个写出器
 * 负责写出自身；{@link ResponseWriterManager} 按声明类型先查内置表、再查自定义表，
 * 均未命中时落默认写出器兜底。
 * {@link #write} 执行写出动作，结果以 {@link WriteResult} 返回：
 * 可监听（{@link WriteType#LISTENABLE}）时携带写出完成凭据（{@link ChannelFuture}）
 * 供调用方挂接后续动作；不可监听（{@link WriteType#NOT_LISTENABLE}）时无凭据
 * （如忽略写出），调用方按状态判断即可，无须对凭据做空值检查。
 * </p>
 */
public interface ResponseWriter {

    /**
     * 写出凭据状态：本次写出是否生成了可供上游监听的完成凭据。
     */
    enum WriteType {
        /** 可监听：写出产生了完成凭据，凭据本身携带写出的成败终态。 */
        LISTENABLE,
        /** 不可监听：写出未产生凭据（如忽略写出、SSE 发射器仅初始化、写出异常已关闭连接）。 */
        NOT_LISTENABLE
    }

    /**
     * 写出结果：凭据状态 {@code writeType} 与写出完成凭据 {@code channelFuture}。
     * <p>
     * 不变式：{@link WriteType#LISTENABLE} 时凭据非空，
     * {@link WriteType#NOT_LISTENABLE} 时凭据为 {@code null}；
     * 经静态工厂 {@link #listenable}/{@link #notListenable()} 构造以保证。
     * 凭据可为失败终态（如写头失败），写出失败不改变"可监听"属性。
     */
    record WriteResult(WriteType writeType, ChannelFuture channelFuture) {

        /**
         * 以写出凭据构造可监听结果。
         *
         * @param channelFuture 写出完成凭据，不能为null
         * @throws NullPointerException 凭据为null
         */
        public static WriteResult listenable(ChannelFuture channelFuture) {
            Objects.requireNonNull(channelFuture, "A listenable write result must carry a channel future");
            return new WriteResult(WriteType.LISTENABLE, channelFuture);
        }

        /**
         * 构造不可监听结果：本次写出无凭据，上游不可挂接监听。
         *
         * @return 无写出凭据的结果
         */
        public static WriteResult notListenable() {
            return new WriteResult(WriteType.NOT_LISTENABLE, null);
        }
    }

    /**
     * 注入磁盘IO线程池，供需要调度磁盘读取任务的写出器（文件流响应）使用；
     * 不消费线程池的写出器存储后不使用，构造期可统一调用。
     * 注入按实例一次性生效：同一写出器实例重复注入抛
     * {@link IllegalStateException}（继承 {@link AbstractResponseWriter} 的写出器由基类提供该护栏）。
     *
     * @param threadPool 磁盘IO线程池
     * @throws IllegalStateException 本实例已注入过线程池，不能重复注入
     */
    void injectThreadPool(ThreadPool threadPool);

    /**
     * 写出响应。
     *
     * @param httpResponse 响应对象
     * @param session      当前连接会话，提供写出通道
     * @return 写出结果：可监听时携带完成凭据，不可监听时凭据为 {@code null}
     */
    WriteResult write(HttpResponse httpResponse, InternalHttpConnection session);
}
