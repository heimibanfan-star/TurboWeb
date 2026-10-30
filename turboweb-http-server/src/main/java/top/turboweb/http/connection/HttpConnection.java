package top.turboweb.http.connection;

import top.turboweb.http.core.StatusLine;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP 连接契约接口，封装一条客户端连接上的数据写出、关闭监听与连接关闭能力。
 * <p>
 * 主要服务于 SSE 等需要在常规响应之外持续向客户端写出数据的场景：
 * {@link #write(byte[])} 向客户端写出一块数据并返回写出结果的异步凭据，
 * {@link #closeListener(Runnable)} 注册连接关闭时的回调，
 * {@link #close()} 主动关闭连接。
 * </p>
 */
public interface HttpConnection {

    /**
     * 以状态行与消息头集合构成响应首部，写出一条响应消息头到连接上。
     * <p>按调用顺序先于后续 {@link #write(byte[])} 写出的响应体到达客户端；
     * 同一头名的取值序列逐条写出，不合并。</p>
     *
     * @param statusLine 状态行，承载状态码与原因短语
     * @param headers    消息头键值集合：键为头名，值为该头名的取值序列
     * @return 写出结果的异步凭据，首部写出成功时正常完成，写出失败时以异常结束
     */
    CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers);

    /**
     * 写出一条响应消息体的结束标记（终止块），宣告本轮响应消息体已完整写出。
     * <p>与响应首部、响应体共同构成一条完整响应消息，写出终止块后不主动关闭连接。</p>
     *
     * @return 写出结果的异步凭据，结束标记写出成功时正常完成，写出失败时以异常结束
     */
    CompletableFuture<Void> finishWrite();

    /**
     * 向客户端写出一块数据。
     * <p>多次写出的数据按调用顺序到达客户端。</p>
     *
     * @param bytes 数据
     * @return 写出结果的异步凭据，写出成功时正常完成，写出失败时以异常结束
     */
    CompletableFuture<Void> write(byte[] bytes);

    /**
     * 注册连接关闭时触发的回调。
     *
     * @param runnable 回调
     */
    void closeListener(Runnable runnable);

    /**
     * 获取对端地址。
     *
     * @return 远程地址
     */
    SocketAddress remoteAddress();

    /**
     * 获取本地地址。
     *
     * @return 本地地址
     */
    SocketAddress localAddress();

    /**
     * 关闭连接。
     *
     * @return 关闭结果的异步凭据，连接关闭完成时正常完成，关闭失败时以异常结束
     */
    CompletableFuture<Void> close();

}
