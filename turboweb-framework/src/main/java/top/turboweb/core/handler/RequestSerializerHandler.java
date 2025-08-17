package top.turboweb.core.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.utils.thread.ThreadAssert;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 保证HTTP请求的顺序
 */
public class RequestSerializerHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestSerializerHandler.class);
    private final Queue<FullHttpRequest> cacheRequest = new LinkedList<>();
    private boolean isProcessing = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ThreadAssert.assertIsEventLoop(ctx.channel().eventLoop());
        if (msg instanceof FullHttpRequest fullHttpRequest) {
            // 判断当前连接是否有请求正在处理
            if (isProcessing) {
                int maxCacheRequestNum = 8;
                if (cacheRequest.size() >= maxCacheRequestNum) {
                    log.warn("RequestSerializerHandler cacheRequest is full");
                    ctx.close();
                }
                cacheRequest.add(fullHttpRequest);
                // 中断对后续处理器的调用
                return;
            }
        }
        isProcessing = true;
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            super.write(ctx, msg, promise);
        } finally {
            // 判断当前消息的类型是否是结束信号
            if (msg instanceof LastHttpContent) {
                ctx.channel().eventLoop().execute(() -> {
                    isProcessing = false;
                    // 判断是否有缓冲的消息
                    if (cacheRequest.isEmpty()) {
                        return;
                    }
                    // 获取缓冲的请求
                    FullHttpRequest request = cacheRequest.poll();
                    // 恢复当前请求
                    try {
                        this.channelRead(ctx, request);
                    } catch (Exception e) {
                        log.error("RequestSerializerHandler error", e);
                        ctx.close();
                    }
                });
            }
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 释放资源
        while (!cacheRequest.isEmpty()) {
            try {
                FullHttpRequest httpRequest = cacheRequest.poll();
                httpRequest.release();
            } catch (Exception ignore) {
            }
        }
    }
}
