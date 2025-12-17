package top.turboweb.http.connect;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.net.SocketAddress;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 服务器内部使用的连接会话
 */
public class InternalConnectSession implements ConnectSession{

    protected final Channel channel;

    /**
     * 每个channel拥有自己的一把锁，保护对该channel的写入原子性
     */
    public final ReentrantLock channelLock = new ReentrantLock();

    public InternalConnectSession(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public EventLoop getExecutor() {
        return channel.eventLoop();
    }

    @Override
    public ChannelFuture send(String message) {
        ByteBuf buf = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8);
        DefaultHttpContent content = new DefaultHttpContent(buf); // 发送 chunked 数据
        return channel.writeAndFlush(content);
    }

    @Override
    public void closeListener(Runnable runnable) {
        channel.closeFuture().addListener(future -> {
            runnable.run();
        });
    }

    @Override
    public void close() {
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                return;
            }
            // 刷新数据
            channel.flush();
            // 发送结束信号
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(future -> {
                channel.close();
            });
        });
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.localAddress();
    }

}
