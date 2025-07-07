package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
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
 * AIO文件响应策略
 */
public class AsyncFileResponseStrategy extends ResponseStrategy {
    private static final Logger log = LoggerFactory.getLogger(AsyncFileResponseStrategy.class);

    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        ChannelFuture future = null;
        if (response instanceof AsyncFileResponse asyncFileResponse) {
            // 写入响应头
            future =  session.getChannel().writeAndFlush(asyncFileResponse);
            WorkStealThreadUtils.execute(() -> handleAsyncFileResponse(asyncFileResponse, session));
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
        return future;
    }

    /**
     * 处理异步文件响应
     * @param response 响应
     * @param session 会话
     */
    private void handleAsyncFileResponse(AsyncFileResponse response, InternalConnectSession session) {
        // 获取剩余的内容大小
        long remaining = response.getRemaining();
        if (remaining <= 0) {
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
     * 关闭文件通道
     * @param response 响应
     */
    private void closeFileChannel(AsyncFileResponse response) {
        try {
            response.close();
        } catch (IOException e) {
            log.error("关闭文件通道时出现错误", e);
        }
    }

}
