package top.turboweb.http.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import top.turboweb.http.connection.HttpConnection;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Netty Channel 的 {@link HttpConnection} 实现，服务器内部使用
 */
public class InternalHttpConnection implements HttpConnection {

    protected final Channel channel;

    /**
     * 每个channel拥有自己的一把锁，保护对该channel的写入原子性
     */
    public final ReentrantLock channelLock = new ReentrantLock();

    public InternalHttpConnection(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public EventLoop getExecutor() {
        return channel.eventLoop();
    }

    @Override
    public CompletableFuture<Void> write(byte[] bytes) {
        DefaultHttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer(bytes));
        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.writeAndFlush(content).addListener(f -> {
            if (f.cause() != null) {
                future.completeExceptionally(f.cause());
            } else {
                future.complete(null);
            }
        });
        return future;
    }

    @Override
    public void closeListener(Runnable runnable) {
        channel.closeFuture().addListener(future -> {
            runnable.run();
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                future.complete(null);
                return;
            }
            // 刷新数据
            channel.flush();
            // 发送结束信号
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(f -> channel.close().addListener(closeFuture -> {
                if (closeFuture.cause() != null) {
                    future.completeExceptionally(closeFuture.cause());
                } else {
                    future.complete(null);
                }
            }));
        });
        return future;
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

}
