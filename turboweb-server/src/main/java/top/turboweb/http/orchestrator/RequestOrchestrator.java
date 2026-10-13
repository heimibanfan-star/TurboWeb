package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.strategy.ResponseStrategy;
import top.turboweb.http.scheduler.strategy.ResponseStrategyContext;

import java.util.Objects;

/**
 * 请求编排器：负责请求处理链的执行与响应写出。
 * <p>
 * 在调用线程上同步执行 {@link Processor} 责任链，取得响应后经
 * {@link ResponseStrategyContext} 按响应类型选择写出策略并发送；
 * 无论处理成功或抛出异常，编排结束时都会释放请求对象。
 * </p>
 * <p>
 * 写出结果以 {@link ChannelFuture}（写出的完成凭据）返回，供调用方挂接后续动作
 * （如请求日志）；所选策略无写出凭据时（如忽略策略）返回 {@code null}，
 * 调用方应做空值检查。本类不管理线程与限流，任务提交与并发控制属于调度器职责。
 * </p>
 * <p><b>线程安全性：</b>与调度器同形，实例设计为单例共享，可在任意线程调用。</p>
 */
public class RequestOrchestrator {

    /** 请求处理的核心执行链（核心链或业务处理链）。 */
    private final Processor processorChain;
    /** 响应策略上下文，用于选择合适的响应写出方式。 */
    private final ResponseStrategyContext responseStrategyContext;

    /**
     * 创建请求编排器。
     *
     * @param processorChain 请求处理链
     * @param threadPool     磁盘IO线程池，向下注入 {@link ResponseStrategyContext}
     *                       供文件流响应策略调度分块读取；编排器仅使用不负责其关闭
     * @param enableLimit    是否启用响应限流，透传给 {@link ResponseStrategyContext}（仅影响 Reactor 响应类型）
     * @throws NullPointerException 线程池为null
     */
    public RequestOrchestrator(Processor processorChain, ThreadPool threadPool, boolean enableLimit) {
        this.processorChain = processorChain;
        this.responseStrategyContext = new ResponseStrategyContext(enableLimit,
                Objects.requireNonNull(threadPool, "磁盘IO线程池不能为null"));
    }

    /**
     * 编排一次 HTTP 请求：执行处理链、写出响应并释放请求。
     * <p>
     * 处理链在当前线程同步执行；链上抛出的异常不被捕获，请求释放后原样向调用方传播。
     *
     * @param request 完整的 HTTP 请求对象
     * @param session 当前连接的会话上下文
     * @return 响应写出的完成凭据；策略无凭据时（如忽略策略）为 {@code null}
     */
    public ChannelFuture orchestrate(FullHttpRequest request, ConnectSession session) {
        try {
            HttpResponse response = processorChain.invoke(request, session);
            return writeResponse(session, response);
        } finally {
            request.release();
        }
    }

    /**
     * 写出响应。
     * <p>
     * 通过 {@link ResponseStrategyContext} 按响应类型动态选择写出策略并执行。
     *
     * @param session  当前连接会话
     * @param response 响应对象
     * @return 写出的完成凭据；策略无凭据时（如忽略策略）为 {@code null}
     */
    public ChannelFuture writeResponse(ConnectSession session, HttpResponse response) {
        ResponseStrategy responseStrategy = responseStrategyContext.chooseStrategy(response);
        return responseStrategy.handle(response, (InternalConnectSession) session);
    }

}
