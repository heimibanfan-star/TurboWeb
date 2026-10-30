package top.turboweb.http.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import top.turboweb.http.core.StatusLine;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
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

    /**
     * {@inheritDoc}
     * <p>以 Netty 响应消息（{@link DefaultHttpResponse}）承载状态行与消息头写出，
     * 状态行经构造期校验，非法状态码或非法头名的同步异常在此转为异步失败凭据。</p>
     */
    @Override
    public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            HttpResponseStatus status = new HttpResponseStatus(statusLine.code(), statusLine.reason());
            HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
            HttpHeaders httpHeaders = httpResponse.headers();
            headers.forEach(httpHeaders::set);
            channel.writeAndFlush(httpResponse).addListener(f -> {
                if (f.cause() != null) {
                    future.completeExceptionally(f.cause());
                } else {
                    future.complete(null);
                }
            });
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * {@inheritDoc}
     * <p>以 Netty 终止块 {@link LastHttpContent#EMPTY_LAST_CONTENT} 作为结束标记写出。</p>
     */
    @Override
    public CompletableFuture<Void> finishWrite() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(f -> {
            if (f.cause() != null) {
                future.completeExceptionally(f.cause());
            } else {
                future.complete(null);
            }
        });
        return future;
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
