package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.utils.thread.WorkStealThreadUtils;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.AsyncFileResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;

/**
 * 基于 AIO（Asynchronous File I/O）的文件响应策略。
 * <p>
 * 本策略用于处理 {@link AsyncFileResponse} 类型的响应，通过
 * {@link AsynchronousFileChannel} 实现文件内容的异步分块读取与分块写入，
 * 实现真正的流式非阻塞文件传输。
 * </p>
 *
 * <p>
 * 相较于传统阻塞式文件传输，本策略的特点包括：
 * <ul>
 *   <li>采用 AIO 读取文件内容，不阻塞业务线程或 Netty I/O 线程；</li>
 *   <li>基于分块（chunked）编码连续发送文件内容，适配大文件传输；</li>
 *   <li>在传输完成或异常时安全关闭文件通道并释放资源。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 该策略通常由 {@link top.turboweb.http.scheduler.strategy.ResponseStrategyContext}
 * 在检测到响应类型为 {@link AsyncFileResponse} 时自动选用。
 * </p>
 *
 * @see AsyncFileResponse
 * @see AsynchronousFileChannel
 * @see ResponseStrategy
 */
public class AsyncFileResponseStrategy extends ResponseStrategy {
    private static final Logger log = LoggerFactory.getLogger(AsyncFileResponseStrategy.class);

    /**
     * 执行 AIO 文件响应的处理。
     * <p>
     * 首先写出响应头部（状态行与基础头字段），
     * 随后在异步线程池中调度文件内容的分块异步读取与写出。
     * </p>
     *
     * @param response HTTP 响应对象，必须为 {@link AsyncFileResponse} 类型。
     * @param session  当前连接的内部会话对象。
     * @return {@link ChannelFuture} 表示响应头写入的异步结果。
     * @throws IllegalArgumentException 当响应类型不是 {@link AsyncFileResponse} 时抛出。
     */
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        ChannelFuture future = null;
        if (response instanceof AsyncFileResponse asyncFileResponse) {
            // 写入响应头
            future =  session.getChannel().writeAndFlush(asyncFileResponse);
            future.addListener(f -> {
               if (f.isSuccess()) {
                   WorkStealThreadUtils.execute(() -> handleAsyncFileResponse(asyncFileResponse, session));
               }
            });

        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
        return future;
    }

    /**
     * 处理单个分块的异步文件传输。
     * <p>
     * 使用 {@link AsynchronousFileChannel#read(ByteBuffer, long, Object, CompletionHandler)}
     * 进行文件分块读取。每当一个分块读取成功后，会被包装为 {@link DefaultHttpContent}
     * 并立即通过 Netty 管道发送给客户端。
     * </p>
     * <p>
     * 若已传输完成或发生异常，将自动关闭文件通道。
     * </p>
     *
     * @param response 文件响应对象，包含文件通道、当前位置及剩余长度等信息。
     * @param session  当前连接的内部会话对象。
     */
    private void handleAsyncFileResponse(AsyncFileResponse response, InternalConnectSession session) {
        // 获取剩余的内容大小
        long remaining = response.getRemaining();
        if (remaining <= 0) {
            // 发送结束信号
            session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            closeFileChannel(response);
            return;
        }
        // 获取缓冲区
        ByteBuffer buffer = response.chunkBuffer();
        buffer.clear();
        // 获取文件通道
        AsynchronousFileChannel fileChannel = response.getAsynchronousFileChannel();
        // 内容读取
        fileChannel.read(buffer, response.getPosition(), null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result <= 0) {
                    closeFileChannel(response);
                    return;
                }
                buffer.flip();
                // 包装为netty的bytebuf
                ByteBuf buf = Unpooled.wrappedBuffer(buffer);
                // 设置写入的位之
                buf.writerIndex(result);
                // 设置游标
                response.setPosition(response.getPosition() + result);
                // 写入响应
                session.getChannel().writeAndFlush(new DefaultHttpContent(buf)).addListener(future -> {
                    if (future.isSuccess()) {
                        // 继续读取后续分块
                        WorkStealThreadUtils.execute(() -> handleAsyncFileResponse(response, session));
                    } else {
                        // 关闭文件通带
                        closeFileChannel(response);
                        // 断开连接
                        session.close();
                    }
                });
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                // 关闭文件通道
                closeFileChannel(response);
                // 断开连接
                session.close();
            }
        });
    }

    /**
     * 关闭文件通道并捕获可能的 I/O 异常。
     *
     * @param response 文件响应对象。
     */
    private void closeFileChannel(AsyncFileResponse response) {
        try {
            response.close();
        } catch (IOException e) {
            log.error("关闭文件通道时出现错误", e);
        }
    }

}
