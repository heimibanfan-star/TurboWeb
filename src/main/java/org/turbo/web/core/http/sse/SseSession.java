package org.turbo.web.core.http.sse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.turbo.web.exception.TurboSseException;

/**
 * sse的回话对象
 */
public class SseSession {
    private final Channel channel;
    private final Promise<Boolean> promise;

    public SseSession( Channel channel, Promise<Boolean> promise) {
        this.channel = channel;
        this.promise = promise;
    }

    /**
     * 向浏览器写入内容
     *
     * @param message 消息
     */
    public ChannelFuture send(String message) {
        String msg = "data: " + message + "\n\n";
        ByteBuf buf = Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8);
        DefaultHttpContent content = new DefaultHttpContent(buf); // 发送 chunked 数据
        return channel.writeAndFlush(content);
    }

    /**
     * 当连接关闭时触发的回调
     *
     * @param runnable 回调
     */
    public void closeListener(Runnable runnable) {
        promise.addListener(future -> {
            runnable.run();
        });
    }

    /**
     * 关闭连接
     */
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

}
