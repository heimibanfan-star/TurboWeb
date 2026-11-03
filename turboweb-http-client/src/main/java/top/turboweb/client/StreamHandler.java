package top.turboweb.client;

import top.turboweb.http.core.Headers;
import top.turboweb.http.core.StatusLine;

/**
 * 流式响应回调：响应头、响应体分块与终态按到达顺序依次回调，
 * 每条流 {@link #onComplete()} 与 {@link #onThrowable(Throwable)} 恰好到达其一，
 * 永不双发、永不悬空，资源清理锚在终态回调上。
 *
 * <p>回调在引擎的 netty event loop 线程直接触发，回调内不得阻塞；
 * 四个回调均无缺省实现，实现类须逐个面对。</p>
 */
public interface StreamHandler {

    /**
     * 收到响应头时回调：此后开始递送响应体分块。
     *
     * @param statusLine 状态行
     * @param headers    响应头实体
     */
    void onHeaders(StatusLine statusLine, Headers headers);

    /**
     * 收到响应体分块时回调：数组为引擎拷出的本次分块，归调用方所有，引擎不复用。
     *
     * @param chunk 响应体分块
     */
    void onData(byte[] chunk);

    /**
     * 响应完毕时回调：全部分块递送完毕后到达，与失败回调互斥。
     */
    void onComplete();

    /**
     * 流失败时回调：连接失败、中途断开、超时等传输失败由此到达，
     * 与 {@link #onComplete()} 互斥且必达其一。
     *
     * @param t 失败原因
     */
    void onThrowable(Throwable t);
}
