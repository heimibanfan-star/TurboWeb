package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.core.response.ZeroCopyResponse;

import java.util.concurrent.ExecutionException;

/**
 * 零拷贝文件传输响应写出器实现类。
 * <p>
 * 本写出器用于处理 {@link ZeroCopyResponse} 类型的响应，
 * 通过 Netty 的零拷贝（Zero-Copy）机制实现高性能文件传输。
 * 与常规文件流不同，零拷贝传输直接将文件内容从内核缓冲区发送到网络通道，
 * 避免了用户态与内核态之间的数据复制，大幅减少 CPU 开销。
 * </p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *     <li>发送 HTTP 响应头。</li>
 *     <li>通过 {@link io.netty.channel.DefaultFileRegion} 执行文件内容的零拷贝传输。</li>
 *     <li>写入 {@link LastHttpContent#EMPTY_LAST_CONTENT} 标识响应结束。</li>
 * </ul>
 *
 * <p><b>线程模型：</b></p>
 * 本写出器仅允许在虚拟线程（Virtual Thread）中执行，
 * 写出前由本类校验当前线程，非虚拟线程调用抛 {@link IllegalStateException}。
 *
 * <p><b>异常处理：</b></p>
 * <ul>
 *     <li>若响应类型非 {@link ZeroCopyResponse}，则抛出 {@link IllegalArgumentException}。</li>
 *     <li>若文件写入或传输过程中发生 {@link InterruptedException} 或 {@link ExecutionException}，将关闭会话并标记失败。</li>
 * </ul>
 */
public class ZeroCopyResponseWriter extends AbstractResponseWriter {

    /**
     * 执行零拷贝文件响应处理。
     *
     * @param response 响应对象，必须为 {@link ZeroCopyResponse}
     * @param session  当前连接会话，封装了 Netty 的通道信息
     * @return 异步执行结果的 {@link ChannelFuture}
     * @throws IllegalArgumentException 当响应类型不受支持时抛出
     */
    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
        // 确保当前线程为虚拟线程
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalStateException("This method must be called from a virtual thread");
        }
        if (response instanceof ZeroCopyResponse zeroCopyResponse) {
            ChannelPromise promise = session.getChannel().newPromise();
            try {
                // 写入响应头
                session.getChannel().writeAndFlush(zeroCopyResponse).get();
                // 写入数据部分
                session.getChannel().writeAndFlush(zeroCopyResponse.getFileRegion()).get();
                // 写入结束标识
                session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                        .addListener(future -> {
                           if (future.isSuccess()) {
                               promise.setSuccess();
                           } else {
                               promise.setFailure(future.cause());
                               session.close();
                           }
                        });
            } catch (InterruptedException | ExecutionException e) {
                promise.setFailure(e);
                session.close();
            }
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }
}
