package top.turboweb.http.orchestrator.writer;

import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.server.runtime.ResourceProvider;

/**
 * 响应写出处理器契约：将一条响应消息异步写出到一条连接上，
 * 通过 {@link Callback} 向调用方报告写出结果。
 * <p>
 * {@link #write} 发起写出并返回。回调必然且恰好被调用一次，不受写出方式与结果影响：
 * 以 {@link CallbackType#FINISHED} 报告本轮写出的正常结束（成功或失败）；
 * 当响应被忽略、或连接属于长生命周期（无有限写出终态）时，
 * 以 {@link CallbackType#IGNORE} 报告一次忽略。回调错误参数在成功时为
 * {@code null}，在失败时携带具体的异常对象。
 * </p>
 */
public interface MessageWriteHandler {

    /**
     * 写出结果阶段。
     */
    enum CallbackType {
        /** 本轮写出已结束，成功或失败。 */
        FINISHED,
        /** 响应被忽略或连接为长生命周期，无有限写出终态。 */
        IGNORE
    }

    /**
     * 写出回调：报告本轮写出的唯一结果。
     * <p>
     * 上报约定：无论结果如何——正常结束、响应被忽略还是连接为长生命周期——
     * 本回调必然且恰好被调用一次。结果阶段 {@link CallbackType#FINISHED} 表示
     * 正常结束，随其上报的错误参数说明成败——{@code null} 表示写出成功，
     * 非 {@code null} 表示写出失败并携带具体异常对象；
     * {@link CallbackType#IGNORE} 表示响应被忽略或连接为长生命周期，
     * 其上错误参数恒为 {@code null}。
     * </p>
     *
     */
    @FunctionalInterface
    interface Callback {

        /**
         * 上报本轮写出的唯一结果。
         *
         * @param error 失败时的具体异常，成功或忽略时为 {@code null}
         * @param type  写出结果阶段
         */
        void onFinally(Throwable error, CallbackType type);
    }

    /**
     * 初始化处理器的资源来源。
     * <p>
     * 装配期调用一次，注入资源提供者供实现按需取用系统内置或自定义资源。
     * </p>
     *
     * @param resourceProvider 资源提供者，只读查询视图，不含释放操作
     */
    default void init(ResourceProvider resourceProvider) {
    }

    /**
     * 将一条响应消息异步写出到一条连接上。
     * <p>
     * 本方法不保证在返回前已结束写出，本轮写出的唯一结果统一经由回调上报：
     * 无论正常结束、响应被忽略还是连接为长生命周期，{@link Callback} 都必然且
     * 恰好被调用一次。
     * </p>
     *
     * @param connection 目标连接，提供写出通道
     * @param message    待写出的响应消息
     * @param callback   写出回调，报告本轮写出的唯一结果，不能为 {@code null}
     */
    void write(HttpConnection connection, HttpResponseMessage<?> message, Callback callback);
}