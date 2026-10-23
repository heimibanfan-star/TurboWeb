package top.turboweb.http.server.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 保证HTTP请求的顺序。
 * <p>
 * 处理器持有连接级状态（缓冲队列与处理中标记），不能跨channel复用，
 * 由 {@link top.turboweb.http.server.runtime.HttpServerRuntime} 在装配每条管道时新建独立实例。
 */
public class RequestSerializerChannelHandler extends ChannelDuplexHandler {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(RequestSerializerChannelHandler.class);
    private final Queue<FullHttpRequest> cacheRequest = new LinkedList<>();
    private boolean isProcessing = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        EventLoop eventLoop = ctx.channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            // 非本channel的事件循环线程调用时，不触碰缓冲队列与处理中标记，
            // 把控制权移交回该channel的事件循环执行，保证状态单线程访问
            eventLoop.execute(() -> {
                try {
                    channelRead(ctx, msg);
                } catch (Exception e) {
                    // 照Netty对每次handler调用的处理口径：异常交回exceptionCaught通道由管线管理
                    ctx.fireExceptionCaught(e);
                }
            });
            return;
        }
        if (msg instanceof FullHttpRequest fullHttpRequest) {
            // 判断当前连接是否有请求正在处理
            if (isProcessing) {
                int maxCacheRequestNum = 8;
                if (cacheRequest.size() >= maxCacheRequestNum) {
                    log.warn("RequestSerializerChannelHandler cacheRequest is full");
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
                    // 恢复当前请求：channelRead的受检异常不作为断连依据，
                    // 交回exceptionCaught通道由管线按Netty口径管理
                    try {
                        this.channelRead(ctx, request);
                    } catch (Exception e) {
                        ctx.fireExceptionCaught(e);
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
