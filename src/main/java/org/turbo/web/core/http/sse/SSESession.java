package org.turbo.web.core.http.sse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Promise;
import org.turbo.web.exception.TurboSseException;

/**
 * sse的回话对象
 */
public class SSESession {

    private final EventLoop channelExecutor;
    private final Channel channel;
    private final Promise<Boolean> promise;

    public SSESession(EventLoop channelExecutor, Channel channel, Promise<Boolean> promise) {
        this.channelExecutor = channelExecutor;
        this.channel = channel;
        this.promise = promise;
    }

    /**
     * 向浏览器写入内容
     *
     * @param message 消息
     */
    public void send(String message) {
        if (!channel.isActive()) {
            throw new TurboSseException("链接已关闭，不能推送数据");
        }
        channelExecutor.execute(() -> {
            String msg = "data: " + message + "\n\n";
            ByteBuf buf = Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8);
            DefaultHttpContent content = new DefaultHttpContent(buf); // 发送 chunked 数据
            channel.writeAndFlush(content);
        });
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
        channelExecutor.execute(() -> {
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
