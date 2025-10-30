package top.turboweb.core.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.exception.TurboResponseException;

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
     * 单个 HTTP/2 流的最大数据大小，用于分块发送
     */
    private int frameSize = DEFAULT_MAX_FRAME_SIZE;

    /**
     * 默认的 HTTP/2 帧大小
     */
    private static final int DEFAULT_MAX_FRAME_SIZE = 16 * 1024;

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
     * 支持自动拆分超过 {@link #frameSize} 的数据。
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
                    ctx.write(http2HeadersFrame, promise)
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
     * <p>
     * 根据 {@link #frameSize} 将 ByteBuf 拆分为多个 DataFrame 分块发送，最后一帧根据 endStream 参数决定是否结束流。
     * </p>
     *
     * @param ctx       通道上下文
     * @param byteBuf   待写入的数据
     * @param endStream 是否为流结束帧
     */
    private void writeDateFrame(ChannelHandlerContext ctx, ByteBuf byteBuf, boolean endStream, ChannelPromise promise) {
        final int frameSize = this.frameSize;
        if (byteBuf.readableBytes() > frameSize) {
            Flux.<ByteBuf, Integer>generate(byteBuf::readableBytes, (readable, sink) -> {
                        // 判断是否超过分块大小
                        if (readable > frameSize) {
                            // 读取分块
                            ByteBuf chunk = byteBuf.readRetainedSlice(frameSize);
                            sink.next(chunk);
                        } else {
                            // 发送完整的数据
                            sink.next(byteBuf);
                            sink.complete();
                        }
                        return byteBuf.readableBytes();
                    })
                    .subscribe(new BaseSubscriber<ByteBuf>() {

                        @Override
                        protected void hookOnSubscribe(Subscription subscription) {
                            request(1);
                        }

                        @Override
                        protected void hookOnNext(ByteBuf value) {
                            ctx.writeAndFlush(new DefaultHttp2DataFrame(value, false))
                                    .addListener(future -> {
                                        if (!future.isSuccess()) {
                                            promise.tryFailure(future.cause());
                                            ctx.close();
                                            cancel();
                                        } else {
                                            request(1);
                                        }
                                    });
                        }

                        @Override
                        protected void hookOnCancel() {
                            promise.tryFailure(new TurboResponseException("frame stream is cancel"));
                        }

                        @Override
                        protected void hookOnError(Throwable throwable) {
                            promise.tryFailure(throwable);
                        }

                        @Override
                        protected void hookOnComplete() {
                            // 发送一个结束的空帧
                            ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, endStream), promise);
                        }
                    });
        } else {
            // 写入完整的分块
            ctx.writeAndFlush(new DefaultHttp2DataFrame(byteBuf, endStream), promise);
        }
    }
}
