package top.turboweb.core.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import top.turboweb.commons.config.GlobalConfig;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h1>HTTP/2 帧适配处理器</h1>
 * <p>
 * 本类用于在 Netty 中将 HTTP/2 帧（Headers/Data）适配为 FullHttpRequest，并将
 * FullHttpResponse 转换为 HTTP/2 帧进行发送。主要功能包括：
 * </p>
 * <ul>
 *     <li>接收 HTTP/2 Headers/Data 帧并组合为 FullHttpRequest</li>
 *     <li>发送 FullHttpResponse 时，将响应拆分为 HTTP/2 Headers/Data 帧</li>
 *     <li>支持流式分块写入，避免超过最大流大小（maxStreamSize）</li>
 * </ul>
 */
public class Http2FrameAdaptorHandler extends ChannelDuplexHandler {

    /**
     * 当前处理的 HTTP/2 Headers 帧
     */
    private Http2HeadersFrame headersFrame;

    /**
     * 当前处理的 HTTP/2 Data 帧列表，用于组合请求体
     */
    private final LinkedList<Http2DataFrame> dataFrames = new LinkedList<>();

    /**
     * 响应写入状态标识，避免重复写入
     */
    private final AtomicBoolean writing = new AtomicBoolean();

    /**
     * <h2>读取通道数据</h2>
     * <p>
     * 当接收到 HTTP/2 HeadersFrame 或 DataFrame 时，进行缓存并判断是否为最后一个帧。
     * 如果是最后一个帧，则调用 {@link #handleFullHttp2Frame(ChannelHandlerContext)} 处理为 FullHttpRequest。
     * </p>
     *
     * @param ctx 通道上下文
     * @param msg 接收到的消息对象
     * @throws Exception 异常
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame http2HeadersFrame) {
            headersFrame = http2HeadersFrame;
            // 获取客户端请求头中推荐的帧大小

            // 判断是否是最后一个信号
            if (http2HeadersFrame.isEndStream()) {
                handleFullHttp2Frame(ctx);
            }
        } else if (msg instanceof Http2DataFrame http2DataFrame) {
            dataFrames.add(http2DataFrame);
            // 判断是否是最后一个信号
            if (http2DataFrame.isEndStream()) {
                handleFullHttp2Frame(ctx);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * <h2>处理完整的 HTTP/2 请求帧</h2>
     * <p>
     * 将 Headers 和 Data 帧组合为 FullHttpRequest，并触发后续 ChannelHandler 处理。
     * </p>
     *
     * @param ctx 通道上下文
     */
    private void handleFullHttp2Frame(ChannelHandlerContext ctx) {
        try {
            // 获取http2的请求头
            Http2Headers headers = headersFrame.headers();
            Http2StreamChannel streamChannel = (Http2StreamChannel) ctx.channel();
            int streamId = streamChannel.stream().id();
            // 组合请求体的数据
            CompositeByteBuf compositeByteBuf = ctx.alloc().compositeBuffer();
            for (Http2DataFrame dataFrame : dataFrames) {
                compositeByteBuf.addComponent(true, dataFrame.content());
            }
            try {
                FullHttpRequest fullHttpRequest = HttpConversionUtil.toFullHttpRequest(streamId, headers, compositeByteBuf, true);
                ctx.fireChannelRead(fullHttpRequest);
            } catch (Http2Exception e) {
                ctx.close();
            }
        } finally {
            // 释放资源
            this.headersFrame = null;
            this.dataFrames.clear();
        }
    }


    /**
     * <h2>写出通道数据</h2>
     * <p>
     * 将 FullHttpResponse 或 HttpContent 转换为 HTTP/2 Headers/Data 帧发送。
     * </p>
     *
     * @param ctx     通道上下文
     * @param msg     待写入消息
     * @param promise 写操作的 Promise
     * @throws Exception 异常
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (msg instanceof HttpResponse httpResponse) {
                if (writing.get()) {
                    promise.setFailure(new IllegalStateException("writing"));
                    ctx.close();
                }
                // 转化响应头
                HttpHeaders httpHeaders = httpResponse.headers();
                if (httpHeaders.isEmpty()) {
                    httpHeaders.set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=" + GlobalConfig.getResponseCharset());
                }
                Http2Headers headers = HttpConversionUtil.toHttp2Headers(httpResponse.headers(), true);
                headers.status(String.valueOf(httpResponse.status().code()));
                Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(headers);
                // 判断是否是完整的响应
                if (httpResponse instanceof FullHttpResponse fullHttpResponse) {
                    // 写入响应头
                    ctx.write(http2HeadersFrame).addListener(future -> {
                        // 写入失败通知上层，并且关闭连接
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                            ctx.close();
                        } else {
                            // 写入数据部分
                            writeDateFrame(ctx, fullHttpResponse.content(), true, promise);
                        }
                    });
                } else {
                    ctx.writeAndFlush(http2HeadersFrame, promise)
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    writing.set(true);
                                }
                            });
                }
            } else if (msg instanceof HttpContent httpContent) {
                boolean isLast = httpContent instanceof LastHttpContent;
                writeDateFrame(ctx, httpContent.content(), isLast, promise);
                if (isLast) {
                    writing.set(false);
                }
            } else {
                ctx.write(msg, promise);
            }
        } catch (Exception e) {
            promise.tryFailure(e);
        }
    }

    /**
     * <h2>写入 HTTP/2 数据帧</h2>
     * @param ctx       通道上下文
     * @param byteBuf   待写入的数据
     * @param endStream 是否为流结束帧
     */
    private void writeDateFrame(ChannelHandlerContext ctx, ByteBuf byteBuf, boolean endStream, ChannelPromise promise) {
        Http2DataFrame http2DataFrame = new DefaultHttp2DataFrame(byteBuf, endStream);
        ctx.writeAndFlush(http2DataFrame, promise);
    }
}
