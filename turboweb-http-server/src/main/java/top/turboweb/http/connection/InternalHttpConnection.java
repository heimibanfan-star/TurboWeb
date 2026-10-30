package top.turboweb.http.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;

import top.turboweb.http.core.StatusLine;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Netty Channel 的 {@link HttpConnection} 实现，服务器内部使用。
 * <p>
 * 调度期为每个请求分配自增序列号并建立携带该序列号的连接实例（一请求一连接）：
 * 经本连接契约方法与内部直通写出（{@link #writeOut}）的每条出站消息都以
 * {@link SequencedDataChunk} 封章携带序列号，由管道中的定序处理器裁决
 * 直写或缓冲定序。未携带序列号的连接（单参构造）写出不封章，不参与定序，
 * 仅供无定序管道的场景使用。
 */
public class InternalHttpConnection implements HttpConnection {

    /** 未携带序列号的标记：写出不封章、不参与定序 */
    private static final long UNSEQUENCED = -1;

    protected final Channel channel;

    /** 本连接所属响应的序列号 */
    private final long sequence;

    /**
     * 每个channel拥有自己的一把锁，保护对该channel的写入原子性
     */
    public final ReentrantLock channelLock = new ReentrantLock();

    public InternalHttpConnection(Channel channel) {
        this(channel, UNSEQUENCED);
    }

    /**
     * 建立携带序列号的连接：写出的每条消息都以该序列号封章。
     *
     * @param channel  底层通道
     * @param sequence 调度期为本请求分配的响应序列号
     */
    public InternalHttpConnection(Channel channel, long sequence) {
        this.channel = channel;
        this.sequence = sequence;
    }

    public Channel getChannel() {
        return channel;
    }

    public EventLoop getExecutor() {
        return channel.eventLoop();
    }

    /**
     * 获取本连接所属响应的序列号。
     *
     * @return 调度期分配的序列号，未携带序列号的连接返回负值
     */
    public long sequence() {
        return sequence;
    }

    /**
     * 内部直通写出：出站消息按连接序列号封章后写到管道上。
     * 凭据为通道新建的异步结果，写出落定后完成。
     *
     * @param message 原始出站消息
     * @return 写出凭据
     */
    public ChannelFuture writeOut(Object message) {
        ChannelPromise promise = channel.newPromise();
        writeOut(message, promise);
        return promise;
    }

    /**
     * 内部直通写出：出站消息按连接序列号封章后写到管道上，写出结果记入给定凭据。
     *
     * @param message 原始出站消息
     * @param promise 写出凭据
     * @return 传入的凭据
     */
    public ChannelFuture writeOut(Object message, ChannelPromise promise) {
        if (sequence == UNSEQUENCED) {
            channel.writeAndFlush(message, promise);
        } else {
            channel.writeAndFlush(new SequencedDataChunk(sequence, message), promise);
        }
        return promise;
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
            toCredentials(writeOut(httpResponse), future);
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
        toCredentials(writeOut(LastHttpContent.EMPTY_LAST_CONTENT), future);
        return future;
    }

    @Override
    public CompletableFuture<Void> write(byte[] bytes) {
        DefaultHttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer(bytes));
        CompletableFuture<Void> future = new CompletableFuture<>();
        toCredentials(writeOut(content), future);
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
            writeOut(LastHttpContent.EMPTY_LAST_CONTENT).addListener(f -> channel.close().addListener(closeFuture -> {
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

    /**
     * 将写出凭据桥接为异步凭据：写出失败以异常完成，写出成功正常完成。
     */
    private static void toCredentials(ChannelFuture writeFuture, CompletableFuture<Void> credentials) {
        writeFuture.addListener(f -> {
            if (f.cause() != null) {
                credentials.completeExceptionally(f.cause());
            } else {
                credentials.complete(null);
            }
        });
    }
}
