package top.turboweb.core.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.commons.config.GlobalConfig;

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

    private static final InternalLogger log = InternalLoggerFactory.getInstance(Http2FrameAdaptorHandler.class);


    /**
     * 响应写入状态标识，避免重复写入
     */
    private final AtomicBoolean writing = new AtomicBoolean();

    /**
     * <h2>读取通道数据</h2>
     * <p>
     *
     * @param ctx 通道上下文
     * @param msg 接收到的消息对象
     * @throws Exception 异常
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2Frame http2Frame) {
            Http2StreamChannel streamChannel = (Http2StreamChannel) ctx.channel();
            int streamId = streamChannel.stream().id();
            // 转化头部帧
            if (http2Frame instanceof Http2HeadersFrame http2HeadersFrame) {
                HttpRequest httpRequest = HttpConversionUtil.toHttpRequest(streamId, http2HeadersFrame.headers(), true);
                ctx.fireChannelRead(httpRequest);
                // 如果是尾帧，发送结束帧
                if (http2HeadersFrame.isEndStream()) {
                    ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
                }
            }
            // 处理数据帧
            else if (http2Frame instanceof Http2DataFrame http2DataFrame) {
                ByteBuf content = http2DataFrame.content();
                HttpContent httpContent;
                if (http2DataFrame.isEndStream()) {
                    httpContent = new DefaultLastHttpContent(content);
                } else {
                    httpContent = new DefaultHttpContent(content);
                }
                ctx.fireChannelRead(httpContent);
            }
            else {
                log.error("Unsupported message type: {}", msg.getClass().getName());
                ctx.close();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
        super.channelInactive(ctx);
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
