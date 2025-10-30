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

public class Http2FrameAdaptorHandler extends ChannelDuplexHandler {

    private Http2HeadersFrame headersFrame;
    private final LinkedList<Http2DataFrame> dataFrames = new LinkedList<>();
    private boolean writing = false;
    private final int maxStreamSize;

    public Http2FrameAdaptorHandler(int maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame http2HeadersFrame) {
            headersFrame = http2HeadersFrame;

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

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (msg instanceof HttpResponse httpResponse) {
                if (writing) {
                    promise.setFailure(new IllegalStateException("writing"));
                    ctx.close();
                }
                // 设置响应标识符
                writing = true;
                // 转化响应头
                HttpHeaders httpHeaders = httpResponse.headers();
                if (httpHeaders.isEmpty()) {
                    httpHeaders.set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=" + GlobalConfig.getResponseCharset());
                }
                Http2Headers headers = HttpConversionUtil.toHttp2Headers(httpResponse.headers(), true);
                headers.status(String.valueOf(httpResponse.status().code()));
                Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(headers);
                ctx.write(http2HeadersFrame, promise);
                if (httpResponse instanceof FullHttpResponse fullHttpResponse) {
                    writeDateFrame(ctx, fullHttpResponse.content(), true);
                    writing = false;
                }
            } else if (msg instanceof HttpContent httpContent){
                boolean isLast = httpContent instanceof LastHttpContent;
                writeDateFrame(ctx, httpContent.content(), isLast);
                if (isLast) {
                    writing = false;
                }
            }else {
                ctx.write(msg, promise);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeDateFrame(ChannelHandlerContext ctx, ByteBuf byteBuf, boolean endStream) {
        // 判断是否超过最大流大小
        if (byteBuf.readableBytes() > maxStreamSize) {
            // 分块读取
            while (byteBuf.readableBytes() > maxStreamSize) {
                ByteBuf chunk = byteBuf.readRetainedSlice(maxStreamSize);
                ctx.write(new DefaultHttp2DataFrame(chunk, false));
            }
            ctx.write(new DefaultHttp2DataFrame(byteBuf, endStream));
        } else {
            ctx.write(new DefaultHttp2DataFrame(byteBuf, endStream));
        }
    }
}
